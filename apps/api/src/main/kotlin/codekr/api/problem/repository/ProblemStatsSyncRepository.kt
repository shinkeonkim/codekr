package codekr.api.problem.repository

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 저장된 문제 통계를 원자료에서 다시 만든다 (#205).
 *
 * **저장하기로 한 이상 이 경로가 반드시 있어야 한다.** 랭킹(#177)·활동(#105)에서 이미
 * 정한 규칙이다 — 값이 어긋났을 때 되돌릴 방법이 없으면 애초에 저장하면 안 된다.
 *
 * 값이 어긋나는 경우는 여럿이다: 채점 완료, 재채점으로 판정 번복(#107), 제출 소프트
 * 삭제, 회원 탈퇴. **전부 "그 문제를 다시 센다" 하나로 처리한다** — 무엇이 얼마나
 * 바뀌었는지 따라가지 않는다. 증분은 내려갈 때 틀리기 쉽다.
 */
@Repository
class ProblemStatsSyncRepository(private val jdbcClient: JdbcClient) {

    /** 문제 하나를 다시 센다. 제출이 하나도 없으면 0 으로 남는다. */
    fun refresh(problemId: Long) {
        jdbcClient.sql(
            """
            INSERT INTO problem_stats (problem_id, submitters, solvers, updated_at)
            SELECT p.id,
                   count(DISTINCT s.user_id),
                   count(DISTINCT s.user_id) FILTER (WHERE s.verdict = 'ACCEPTED'),
                   now()
            FROM problems p
            LEFT JOIN submissions s
                   ON s.problem_id = p.id AND s.deleted_at IS NULL AND s.kind = 'USER'
            WHERE p.id = :problemId
            GROUP BY p.id
            ON CONFLICT (problem_id) DO UPDATE
                SET submitters = excluded.submitters,
                    solvers = excluded.solvers,
                    updated_at = excluded.updated_at
            """,
        ).param("problemId", problemId).update()
    }

    /**
     * 전부 다시 센다. 어긋났을 때 되돌리는 길이고, 이 기능을 처음 켤 때도 필요하다 —
     * 그 전의 제출은 갱신 경로를 지나지 않았다.
     *
     * @return 다시 센 문제 수.
     */
    fun refreshAll(): Int =
        jdbcClient.sql(
            """
            INSERT INTO problem_stats (problem_id, submitters, solvers, updated_at)
            SELECT p.id,
                   count(DISTINCT s.user_id),
                   count(DISTINCT s.user_id) FILTER (WHERE s.verdict = 'ACCEPTED'),
                   now()
            FROM problems p
            LEFT JOIN submissions s
                   ON s.problem_id = p.id AND s.deleted_at IS NULL AND s.kind = 'USER'
            GROUP BY p.id
            ON CONFLICT (problem_id) DO UPDATE
                SET submitters = excluded.submitters,
                    solvers = excluded.solvers,
                    updated_at = excluded.updated_at
            """,
        ).update()

    /**
     * 저장된 값과 지금 세어 본 값이 다른 문제들 (#205).
     *
     * **어긋나도 아무도 모르는 것이 가장 나쁘다.** 갱신 경로를 하나 빠뜨렸을 때 그것을
     * 알아낼 방법이 이것뿐이다.
     *
     * @return (문제 번호 → 저장된 값, 세어 본 값)
     */
    fun findDrift(): List<StatsDrift> =
        jdbcClient.sql(
            """
            SELECT p.id,
                   coalesce(st.submitters, 0) AS stored_submitters,
                   coalesce(st.solvers, 0)    AS stored_solvers,
                   count(DISTINCT s.user_id)                                     AS actual_submitters,
                   count(DISTINCT s.user_id) FILTER (WHERE s.verdict = 'ACCEPTED') AS actual_solvers
            FROM problems p
            LEFT JOIN problem_stats st ON st.problem_id = p.id
            LEFT JOIN submissions s
                   ON s.problem_id = p.id AND s.deleted_at IS NULL AND s.kind = 'USER'
            GROUP BY p.id, st.submitters, st.solvers
            HAVING coalesce(st.submitters, 0) <> count(DISTINCT s.user_id)
                OR coalesce(st.solvers, 0) <> count(DISTINCT s.user_id) FILTER (WHERE s.verdict = 'ACCEPTED')
            ORDER BY p.id
            """,
        )
            .query { rs, _ ->
                StatsDrift(
                    problemId = rs.getLong("id"),
                    storedSubmitters = rs.getInt("stored_submitters"),
                    storedSolvers = rs.getInt("stored_solvers"),
                    actualSubmitters = rs.getInt("actual_submitters"),
                    actualSolvers = rs.getInt("actual_solvers"),
                )
            }
            .list()
}

/** 저장된 값과 센 값이 어긋난 문제 하나. */
data class StatsDrift(
    val problemId: Long,
    val storedSubmitters: Int,
    val storedSolvers: Int,
    val actualSubmitters: Int,
    val actualSolvers: Int,
)
