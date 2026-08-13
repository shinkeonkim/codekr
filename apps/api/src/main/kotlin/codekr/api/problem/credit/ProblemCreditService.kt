package codekr.api.problem.credit

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.dto.ProblemCreditResponse
import codekr.api.user.entity.WithdrawnUser
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 문제에 이름을 붙이고 읽는다 (#236). */
@Service
class ProblemCreditService(
    private val repository: ProblemCreditRepository,
    private val userRepository: UserRepository,
) {

    /**
     * 이름을 다시 붙인다.
     *
     * **통째로 갈아 끼운다.** 더하기·빼기를 따로 두면 폼이 "지금 무엇이 붙어 있는지" 를
     * 알고 차이를 계산해야 하는데, 그 계산이 어긋나면 지운 사람이 남는다.
     */
    @Transactional
    fun replace(problemId: Long, setterIds: List<Long>, reviewerIds: List<Long>) {
        val ids = (setterIds + reviewerIds).distinct()
        if (ids.isNotEmpty() && userRepository.findAllById(ids).size != ids.size) {
            throw ApiException(ErrorCode.USER_NOT_FOUND, "지정한 회원 중 없는 사람이 있습니다.")
        }

        // **기여자는 지우지 않는다** (#478). 그것은 어드민이 고르는 값이 아니라
        // 신고가 받아들여질 때 붙는 값이라, 함께 지우면 다음 편집에서 이름이 사라진다.
        repository.deleteByIdProblemIdAndIdRoleIn(problemId, listOf(CreditRole.SETTER, CreditRole.REVIEWER))
        repository.saveAll(
            setterIds.distinct().map { ProblemCredit(ProblemCreditId(problemId, it, CreditRole.SETTER)) } +
                reviewerIds.distinct().map { ProblemCredit(ProblemCreditId(problemId, it, CreditRole.REVIEWER)) },
        )
    }

    /**
     * 화면에 보일 이름들.
     *
     * **탈퇴한 사람은 "탈퇴한 사용자" 가 된다** (#140) — 댓글·게시글과 같은 규칙이다.
     * 이름을 지우지 않는 이유는 그 사람이 만든 사실 자체는 남는 것이기 때문이다.
     */
    @Transactional(readOnly = true)
    fun creditsOf(problemId: Long): List<ProblemCreditResponse> {
        val credits = repository.findByIdProblemId(problemId)
        if (credits.isEmpty()) return emptyList()

        val users = userRepository.findAllById(credits.map { it.id.userId }).associateBy { it.id }
        return credits.map { credit ->
            ProblemCreditResponse(
                userId = credit.id.userId,
                nickname = WithdrawnUser.nicknameOf(users[credit.id.userId]),
                role = credit.id.role,
                roleLabel = credit.id.role.label,
            )
        }
    }
}
