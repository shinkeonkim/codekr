package codekr.api.auth.password

import codekr.api.auth.email.MailProperties
import codekr.api.auth.email.MailSender
import codekr.api.auth.security.RevokedTokenRegistry
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.notification.entity.NotificationCategory
import codekr.api.notification.service.NotificationService
import codekr.api.user.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64

/** 비밀번호 재설정 (#315). */
@Service
class PasswordResetService(
    private val repository: PasswordResetRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val mailSender: MailSender,
    private val mailProperties: MailProperties,
    private val notificationService: NotificationService,
    private val revokedTokens: RevokedTokenRegistry,
    private val clock: Clock,
) {

    /**
     * 재설정 메일을 보낸다.
     *
     * **누가 부르는지 모른다.** 아무나 남의 주소로 요청할 수 있으므로, 무엇을 하든
     * 밖에서 보이는 결과는 같아야 한다 — 다르게 답하면 **어느 주소가 가입되어 있는지
     * 확인하는 도구**가 된다.
     */
    @Transactional
    fun request(email: String) {
        val now = clock.instant()
        val user = userRepository.findByEmail(email.trim())

        // 아래 조건들은 전부 **조용히 끝난다.** 밖에서는 구분되지 않는다.
        if (user == null || user.isWithdrawn) return
        // **인증되지 않은 주소로는 보내지 않는다** (#233, #315). 남의 주소를 적고 가입한
        // 계정의 비밀번호를 그 주소 주인이 가져가는 길이 되면 안 된다.
        if (user.emailVerifiedAt == null) return
        // 발송량이 곧 비용이고 평판이다. 반복 요청은 여기서 멎는다.
        repository.findFirstByUserIdOrderByIdDesc(user.id)?.let {
            if (it.createdAt.plus(COOLDOWN).isAfter(now)) return
        }
        if (repository.countSince(user.id, now.minus(Duration.ofDays(1))) >= DAILY_LIMIT) return

        val token = newToken()
        repository.save(PasswordReset(user.id, hash(token), now.plus(TTL)))

        mailSender.send(
            to = user.email,
            subject = "[코드.kr] 비밀번호를 다시 정해 주세요",
            body = """
                아래 주소에서 새 비밀번호를 정할 수 있습니다.

                ${mailProperties.siteUrl}/reset-password?token=$token

                이 링크는 ${TTL.toMinutes()}분 동안만 유효합니다.
                본인이 요청한 것이 아니라면 이 메일을 무시하세요. 비밀번호는 그대로입니다.
            """.trimIndent(),
        )
    }

    /**
     * 새 비밀번호를 정한다.
     *
     * **성공하면 지금 살아 있는 세션을 끊는다.** 비밀번호를 바꾸는 흔한 이유가 "남이
     * 들어와 있는 것 같아서" 인데, 끊지 않으면 그 사람이 계속 들어와 있다.
     */
    @Transactional
    fun reset(token: String, newPassword: String) {
        val now = clock.instant()
        val record = repository.findByTokenHash(hash(token))
            ?: throw ApiException(ErrorCode.VALIDATION_ERROR, "재설정 링크가 올바르지 않습니다.")

        if (record.usedAt != null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이미 사용된 링크입니다.")
        }
        if (record.expiresAt.isBefore(now)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "만료된 링크입니다. 다시 요청해 주세요.")
        }

        val user = userRepository.findById(record.userId)
            .orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }

        record.use(now)
        // PasswordEncoder 계약상 null 이 올 수 없지만 Kotlin 타입으로는 nullable 이다.
        val encoded = passwordEncoder.encode(newPassword) ?: throw ApiException(ErrorCode.INTERNAL_ERROR)
        user.changePassword(encoded, now)

        // 액세스 토큰은 만료 전이라도 통하지 않게 표시하고(#140 과 같은 장치),
        // 갱신 토큰은 `passwordChangedAt` 보다 먼저 발급된 것이 거절된다.
        revokedTokens.revoke(user.id)

        // **본인이 안 했다면 알아야 할 일이다.** 메일과 웹 알림 둘 다로 알린다 —
        // 메일함이 털린 경우에는 메일이 닿지 않을 수 있다 (#106).
        mailSender.send(
            to = user.email,
            subject = "[코드.kr] 비밀번호가 바뀌었습니다",
            body = "방금 비밀번호가 바뀌었습니다. 본인이 한 것이 아니라면 즉시 다시 재설정해 주세요.",
        )
        notificationService.notify(
            userId = user.id,
            category = NotificationCategory.SYSTEM,
            title = "비밀번호가 바뀌었습니다",
            body = "본인이 한 것이 아니라면 즉시 다시 재설정해 주세요.",
        )
    }

    private fun newToken(): String {
        val bytes = ByteArray(32)
        RANDOM.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        val RANDOM = SecureRandom()

        /**
         * 30분.
         *
         * 인증 메일(24시간)보다 훨씬 짧다 — 이 링크는 **로그인 수단 자체를 갈아 끼운다.**
         * 짧으면 메일이 늦은 사람이 못 쓰지만, 그때는 다시 요청하면 된다.
         */
        val TTL: Duration = Duration.ofMinutes(30)
        val COOLDOWN: Duration = Duration.ofMinutes(1)
        const val DAILY_LIMIT = 5
    }
}
