package codekr.api.problem.editorial

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 모범 답안을 언제 보여 줄지 정한다 (#719).
 *
 * **자격이 없으면 없는 것처럼 답한다.** "있지만 못 본다" 를 알려 주면 그 자체가
 * 신호가 된다 — 어떤 문제에 모범 답안이 있는지가 보이고, 대회 중에는 그것만으로도
 * 정보다.
 */
@Service
class ProblemEditorialService(
    private val repository: ProblemEditorialRepository,
    private val jdbcClient: JdbcClient,
) {

    /**
     * 이 사람에게 보여 줄 모범 답안. 없거나 자격이 없으면 `null`.
     *
     * **순서가 중요하다.** 대회를 먼저 본다 — 이미 푼 사람도 대회 중에는 못 본다.
     */
    @Transactional(readOnly = true)
    fun forUser(problemId: Long, userId: Long?): ProblemEditorial? {
        if (userId == null) return null
        if (isInRunningContest(problemId)) return null
        if (!hasSolved(problemId, userId)) return null
        return repository.findById(problemId).orElse(null)
    }

    /**
     * 지금 진행 중인 대회에 든 문제인가.
     *
     * **푼 사람에게도 막는다.** 대회 전에 그 문제를 풀어 둔 사람이 대회 중에 모범
     * 답안을 열 수 있으면, 같은 문제를 처음 보는 참가자와 조건이 달라진다.
     *
     * 진행 여부를 시각으로 판정하는 것은 대회 도메인의 규칙 그대로다(#61) — 상태를
     * 저장하지 않으므로 여기서도 시각을 견준다. 제외된 문제(#86)는 대회에서 빠졌으므로
     * 막을 이유가 없다.
     */
    private fun isInRunningContest(problemId: Long): Boolean =
        jdbcClient.sql(
            """
            SELECT count(*)
              FROM contest_problems cp
              JOIN contests c ON c.id = cp.contest_id
             WHERE cp.problem_id = :p
               AND cp.excluded_at IS NULL
               AND c.deleted_at IS NULL
               AND c.status = 'PUBLISHED'
               AND now() >= c.starts_at
               AND now() < c.ends_at
            """.trimIndent(),
        ).param("p", problemId).query(Int::class.java).single() > 0

    /** 이 문제를 풀었는가. 랭킹 점수 표가 곧 "푼 기록" 이다 (#57) — 난이도 투표와 같은 기준. */
    private fun hasSolved(problemId: Long, userId: Long): Boolean =
        jdbcClient.sql("SELECT count(*) FROM user_problem_scores WHERE problem_id = :p AND user_id = :u")
            .param("p", problemId)
            .param("u", userId)
            .query(Int::class.java)
            .single() > 0

    /** 어드민이 쓰거나 고친다. 없으면 만든다. */
    @Transactional
    fun save(problemId: Long, body: String, referenceAnswer: String?, referenceLabel: String?): ProblemEditorial {
        val editorial = repository.findById(problemId).orElseGet { ProblemEditorial(problemId, body) }
        editorial.body = body
        editorial.referenceAnswer = referenceAnswer?.takeIf { it.isNotBlank() }
        editorial.referenceLabel = referenceLabel?.takeIf { it.isNotBlank() }
        return repository.save(editorial)
    }

    @Transactional(readOnly = true)
    fun forAdmin(problemId: Long): ProblemEditorial? = repository.findById(problemId).orElse(null)

    @Transactional
    fun delete(problemId: Long) {
        if (!repository.existsById(problemId)) throw ApiException(ErrorCode.EDITORIAL_NOT_FOUND)
        repository.deleteById(problemId)
    }
}
