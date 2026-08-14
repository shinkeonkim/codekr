package codekr.api.user.service

import codekr.api.audit.entity.AdminAction
import codekr.api.audit.service.AdminAuditService
import codekr.api.auth.email.EmailVerificationService
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.user.dto.AdminEmailVerificationResponse
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 어드민이 회원의 이메일 인증에 손대는 두 가지 (#524).
 *
 * **메일이 안 가면 그 사람은 글도 댓글도 못 쓴다** (#233 이 정한 규칙이다). 스팸함으로
 * 갔거나(#355), 주소를 오타로 적었거나, 발송이 실패했거나, 하루 한도를 다 썼거나 —
 * 지금까지는 그때 **DB 를 직접 고치는 것 말고 방법이 없었다.**
 */
@Service
class AdminEmailVerificationService(
    private val users: UserRepository,
    private val verification: EmailVerificationService,
    private val audit: AdminAuditService,
    private val clock: Clock,
) {

    /**
     * 인증 메일을 다시 보낸다.
     *
     * **사용자의 한도(60초·하루 5통) 밖에서 보낸다.** 어드민은 그 한도 때문에 막힌
     * 사람을 돕는 자리라, 같은 한도를 걸면 이 기능의 목적이 무너진다.
     *
     * 대신 **갔는지 아닌지를 돌려준다.** 지금까지 발송 실패는 로그에만 남아서, 누른
     * 사람은 성공과 실패를 구분할 수 없었다.
     */
    @Transactional
    fun resend(actorId: Long, userId: Long): AdminEmailVerificationResponse {
        val user = users.findById(userId).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }
        if (user.emailVerifiedAt != null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이미 인증한 회원입니다.")
        }

        val outcome = verification.send(user.id, user.email)
        audit.record(
            actorId = actorId,
            action = AdminAction.EMAIL_VERIFICATION_RESEND,
            targetId = user.id,
            targetLabel = user.nickname,
            detail = outcome.name,
        )
        return AdminEmailVerificationResponse(user.id, user.emailVerifiedAt, mail = outcome)
    }

    /**
     * 확인 없이 인증 처리한다.
     *
     * **되돌리기 어려운 조치다** — 그래서 사유가 필수이고(`AdminAction`), 기록에 남는다.
     * 주소가 진짜인지 아무도 확인하지 않은 상태로 "확인했다" 가 되기 때문이다.
     */
    @Transactional
    fun forceVerify(actorId: Long, userId: Long, reason: String?): AdminEmailVerificationResponse {
        val user = users.findById(userId).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }
        if (user.emailVerifiedAt != null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이미 인증한 회원입니다.")
        }

        user.verifyEmail(clock.instant())
        // 사유가 비어 있으면 여기서 막힌다 — 규칙은 AdminAction 한 곳에만 있다 (#225).
        audit.record(
            actorId = actorId,
            action = AdminAction.EMAIL_VERIFY_FORCED,
            targetId = user.id,
            targetLabel = user.nickname,
            reason = reason,
        )
        // 메일을 보내지 않았으므로 `mail` 은 비운다.
        return AdminEmailVerificationResponse(user.id, user.emailVerifiedAt)
    }
}
