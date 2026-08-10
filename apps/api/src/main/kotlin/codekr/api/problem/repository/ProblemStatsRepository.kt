package codekr.api.problem.repository

import codekr.api.problem.dto.ProblemStats
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 문제별 풀이 통계 (#84).
 *
 * **집계를 저장하지 않고 조회 시점에 센다.** 저장하면 재채점(#63)이나 제출 삭제 때 값이
 * 어긋나고, 어긋난 것을 되돌리는 장치를 또 만들어야 한다. 지금 규모에서는 세는 편이 싸고
 * 항상 맞다 — 부분 인덱스가 있어 문제당 인덱스 스캔으로 끝난다.
 *
 * 느려지면 캐시를 얹는다. 그때도 **원자료는 submissions 하나**로 남는다.
 */
@Repository
class ProblemStatsRepository(private val jdbcClient: JdbcClient) {

    /** 여러 문제의 통계를 한 번에 읽는다. 목록 화면이 문제마다 질의하지 않게. */
    fun findAll(problemIds: Collection<Long>): Map<Long, ProblemStats> {
        if (problemIds.isEmpty()) return emptyMap()

        return jdbcClient.sql(
            """
            SELECT problem_id,
                   count(DISTINCT user_id)                                        AS submitters,
                   count(DISTINCT user_id) FILTER (WHERE verdict = 'ACCEPTED')    AS solvers
            FROM submissions
            WHERE deleted_at IS NULL
              AND kind = 'USER'
              AND problem_id IN (:problemIds)
            GROUP BY problem_id
            """,
        )
            .param("problemIds", problemIds)
            .query { rs, _ ->
                rs.getLong("problem_id") to ProblemStats(rs.getInt("submitters"), rs.getInt("solvers"))
            }
            .list()
            .toMap()
    }

    fun findOne(problemId: Long): ProblemStats = findAll(listOf(problemId))[problemId] ?: ProblemStats.EMPTY
}
