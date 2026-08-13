package codekr.api.auth.withdrawal

import codekr.api.audit.entity.AdminAction
import codekr.api.auth.security.RevokedTokenRegistry
import codekr.api.audit.service.AdminAuditService
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.affiliation.repository.UserAffiliationRepository
import codekr.api.user.email.repository.UserEmailRepository
import codekr.api.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 회원 탈퇴 (#140).
 *
 * **글과 댓글은 남는다.** 대화에서 한쪽 말만 사라지면 남은 말이 뜻을 잃는다 —
 * 답이 달린 질문을 지우면 답을 쓴 사람의 글까지 무의미해진다.
 *
 * 어드민의 강제 탈퇴도 **같은 경로**를 쓴다. 두 벌로 두면 한쪽만 고치는 일이 생기고,
 * 그때 어느 쪽이 진짜 규칙인지 알 수 없게 된다.
 */
@Service
@Transactional
class WithdrawalService(
    private val auditService: AdminAuditService,
    private val userRepository: UserRepository,
    private val revokedTokens: RevokedTokenRegistry,
    private val userEmails: UserEmailRepository,
    private val userAffiliations: UserAffiliationRepository,
) {

    fun withdraw(userId: Long) = withdraw(userId, actorId = null, reason = null)

    /**
     * 어드민의 강제 탈퇴 (#140, #225).
     *
     * **사유가 필수다.** 되돌릴 수 없고, 계정이 사라진 뒤에 "누가 왜 지웠는지" 를
     * 물으면 기록의 사유가 유일한 답이다.
     *
     * 닉네임을 **지우기 전에** 기록에 사본으로 남긴다 — `withdraw()` 가 그것을 그 자리에서
     * 지우므로 순서가 뒤바뀌면 숫자만 남는다.
     */
    fun withdraw(userId: Long, actorId: Long?, reason: String?) {
        val user = userRepository.findById(userId).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }
        if (user.isWithdrawn) throw ApiException(ErrorCode.USER_NOT_FOUND)

        if (actorId != null) {
            auditService.record(
                actorId = actorId,
                action = AdminAction.FORCE_WITHDRAW,
                targetId = user.id,
                targetLabel = user.nickname,
                reason = reason,
            )
        }

        /*
            **확인한 추가 주소도 지운다** (#396, #140).

            학교·회사 메일이라 실명이 들어 있는 경우가 많다 — 로그인 주소를 지우면서
            이것을 남기면 "식별 정보를 남기지 않는다" 가 반만 지켜진다.
        */
        // 소속을 먼저 뗀다 — 주소를 가리키므로 순서가 뒤바뀌면 참조가 끊긴다 (#398).
        userAffiliations.deleteByUserId(user.id)
        userEmails.deleteByUserId(user.id)

        user.withdraw()
        // 발급된 토큰도 더 이상 통하지 않아야 한다. 만료를 기다리지 않는다.
        revokedTokens.revoke(userId)
        log.info("회원 탈퇴: userId={}", userId)
    }

    private companion object {
        val log = LoggerFactory.getLogger(WithdrawalService::class.java)
    }
}
