package codekr.api.auth.withdrawal

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
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
    private val userRepository: UserRepository,
    private val withdrawnTokens: WithdrawnTokenRegistry,
) {

    fun withdraw(userId: Long) {
        val user = userRepository.findById(userId).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }
        if (user.isWithdrawn) throw ApiException(ErrorCode.USER_NOT_FOUND)

        user.withdraw()
        // 발급된 토큰도 더 이상 통하지 않아야 한다. 만료를 기다리지 않는다.
        withdrawnTokens.revoke(userId)
        log.info("회원 탈퇴: userId={}", userId)
    }

    private companion object {
        val log = LoggerFactory.getLogger(WithdrawalService::class.java)
    }
}
