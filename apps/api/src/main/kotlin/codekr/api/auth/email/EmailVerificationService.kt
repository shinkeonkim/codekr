package codekr.api.auth.email

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.user.email.entity.UserEmail
import codekr.api.user.email.repository.UserEmailRepository
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64

/** 인증 메일을 만들고 확인한다 (#233). */
@Service
class EmailVerificationService(
    private val repository: EmailVerificationRepository,
    private val userRepository: UserRepository,
    private val mailSender: MailSender,
    private val properties: MailProperties,
    private val clock: Clock,
    private val userEmails: UserEmailRepository,
) {

    /**
     * 인증 메일을 보낸다.
     *
     * **가입이 이것 때문에 실패하면 안 된다** — 발송은 [MailSender] 안에서 삼켜지고,
     * 여기서는 토큰 발급까지만 책임진다.
     */
    @Transactional
    fun send(
        userId: Long,
        email: String,
        enforceCooldown: Boolean = false,
        forAddress: String? = null,
    ): MailOutcome {
        val now = clock.instant()

        if (enforceCooldown) {
            repository.findFirstByUserIdAndEmailOrderByIdDesc(userId, forAddress)?.let { last ->
                val next = last.createdAt.plus(COOLDOWN)
                if (next.isAfter(now)) {
                    val seconds = Duration.between(now, next).seconds + 1
                    throw ApiException(ErrorCode.TOO_MANY_REQUESTS, "${seconds}초 뒤에 다시 시도해 주세요.")
                }
            }
            // 발송량이 곧 비용이고 평판이다. 하루 상한을 처음부터 둔다.
            if (repository.countSince(userId, now.minus(Duration.ofDays(1))) >= DAILY_LIMIT) {
                throw ApiException(ErrorCode.TOO_MANY_REQUESTS, "오늘은 더 보낼 수 없습니다. 내일 다시 시도해 주세요.")
            }
        }

        val token = newToken()
        // `forAddress` 가 있으면 **추가 주소**를 확인하는 토큰이다 (#396).
        // 없으면 로그인 주소 — 가입 때 보내는 그것이다.
        repository.save(EmailVerification(userId, hash(token), now.plus(TTL), forAddress))

        val link = "${properties.siteUrl}/verify-email?token=$token"
        return mailSender.send(
            to = email,
            subject = "[코드.kr] 이메일 주소를 확인해 주세요",
            body = """
                아래 주소를 열면 이메일 확인이 끝납니다.

                $link

                이 링크는 ${TTL.toHours()}시간 동안만 유효합니다.
                본인이 요청한 것이 아니라면 이 메일을 무시하세요.
            """.trimIndent(),
        )
    }

    /**
     * 토큰을 확인한다.
     *
     * **한 번만 쓰인다.** 쓴 시각을 남겨 두면 "이미 인증했다" 와 "그런 토큰이 없다" 를
     * 나눠 말할 수 있다 — 앞엣것은 사용자가 링크를 두 번 누른 것뿐이다.
     */
    @Transactional
    fun verify(token: String) {
        val now = clock.instant()
        val record = repository.findByTokenHash(hash(token))
            ?: throw ApiException(ErrorCode.VALIDATION_ERROR, "인증 링크가 올바르지 않습니다.")

        val user = userRepository.findById(record.userId)
            .orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }

        if (record.usedAt != null) {
            // 이미 인증된 계정이면 조용히 성공으로 둔다 — 링크를 두 번 누른 사람에게
            // 오류를 보일 이유가 없다.
            if (user.emailVerifiedAt != null) return
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이미 사용된 링크입니다.")
        }
        if (record.expiresAt.isBefore(now)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "만료된 링크입니다. 인증 메일을 다시 받아 주세요.")
        }

        record.use(now)

        /*
            **어느 주소를 확인한 것인가** (#396).

            추가 주소면 로그인 주소의 확인 여부를 건드리지 않는다 — 학교 메일을
            확인했다고 가입 주소가 확인된 것은 아니다.
        */
        val address = record.email
        if (address == null) {
            user.verifyEmail(now)
            return
        }

        // 기다리는 동안 남이 먼저 확인했을 수 있다. 그때는 붙이지 않는다.
        if (userRepository.findByEmail(address) != null || userEmails.existsByEmail(address)) {
            throw ApiException(ErrorCode.EMAIL_ALREADY_EXISTS)
        }
        userEmails.save(UserEmail(record.userId, address))
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

        /** 하루면 충분하다. 길게 두면 새어 나간 링크가 오래 살아 있는다. */
        val TTL: Duration = Duration.ofHours(24)
        val COOLDOWN: Duration = Duration.ofSeconds(60)
        const val DAILY_LIMIT = 5
    }
}
