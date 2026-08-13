package codekr.api.user.email.service

import codekr.api.auth.email.EmailVerificationService
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.user.email.dto.UserEmailResponse
import codekr.api.user.email.repository.UserEmailRepository
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserEmailService(
    private val emails: UserEmailRepository,
    private val users: UserRepository,
    private val verificationService: EmailVerificationService,
) {

    fun list(userId: Long): List<UserEmailResponse> =
        emails.findByUserIdOrderByIdAsc(userId).map { UserEmailResponse(it.id, it.email, it.verifiedAt) }

    /**
     * 그 주소로 확인 메일을 보낸다.
     *
     * **이미 누가 쓰는 주소면 거절한다.** 그러지 않으면 한 학교 메일로 두 계정이 같은
     * 소속을 얻는다. 로그인 주소도 함께 본다 — 남의 로그인 주소를 내 추가 주소로
     * 확인해 버리면 그 사람의 소속을 가져가는 셈이다.
     */
    @Transactional
    fun requestVerification(userId: Long, email: String) {
        if (users.findByEmail(email) != null || emails.existsByEmail(email)) {
            throw ApiException(ErrorCode.EMAIL_ALREADY_EXISTS)
        }
        // 발송 상한·쿨다운은 인증 서비스가 이미 센다 (#233). 두 벌로 만들지 않는다.
        verificationService.send(userId, email, enforceCooldown = true, forAddress = email)
    }

    /**
     * 뗀다.
     *
     * **남의 것은 못 뗀다.** 그리고 소속(#398)이 이 주소에 붙으므로, 떼면 그 소속도
     * 함께 떨어져야 한다 — 그것은 소속을 만드는 이슈에서 이 자리에 붙인다.
     */
    @Transactional
    fun remove(userId: Long, id: Long) {
        val target = emails.findById(id).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }
        if (target.userId != userId) throw ApiException(ErrorCode.FORBIDDEN)
        emails.delete(target)
    }
}
