package codekr.api.user.repository

import codekr.api.problem.entity.Difficulty
import codekr.api.user.dto.SolvedByTier
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 프로필에 쓰는 집계 (#83).
 *
 * 문제 통계(#84)와 같은 판단이다 — 저장하지 않고 조회 시점에 센다. 원자료가 하나면
 * 재채점이나 삭제로 어긋날 일이 없다.
 */
@Repository
class UserProfileRepository(private val jdbcClient: JdbcClient) {

    /**
     * 푼 문제 수. **제출 수가 아니라 문제 수**다 —
     * 같은 문제를 다섯 번 맞혀도 하나로 센다.
     */
    fun countSolvedProblems(userId: Long): Int =
        jdbcClient.sql(
            """
            SELECT count(DISTINCT problem_id)
            FROM submissions
            WHERE user_id = :userId AND deleted_at IS NULL AND kind = 'USER' AND verdict = 'ACCEPTED'
            """,
        ).param("userId", userId).query(Int::class.java).single()

    fun countSubmissions(userId: Long): Int =
        jdbcClient.sql(
            """
            SELECT count(*)
            FROM submissions
            WHERE user_id = :userId AND deleted_at IS NULL AND kind = 'USER'
            """,
        ).param("userId", userId).query(Int::class.java).single()

    /**
     * 푼 문제의 난이도 분포. 티어 단위로 묶는다 —
     * 30단계를 그대로 보여주면 무엇을 잘하는지 한눈에 안 들어온다.
     */
    fun solvedByTier(userId: Long): List<SolvedByTier> =
        jdbcClient.sql(
            """
            SELECT p.difficulty_level AS level, count(DISTINCT p.id) AS solved
            FROM submissions s
            JOIN problems p ON p.id = s.problem_id
            WHERE s.user_id = :userId
              AND s.deleted_at IS NULL
              AND s.kind = 'USER'
              AND s.verdict = 'ACCEPTED'
            GROUP BY p.difficulty_level
            """,
        )
            .param("userId", userId)
            .query { rs, _ -> Difficulty.ofLevel(rs.getInt("level")).tier to rs.getInt("solved") }
            .list()
            .groupBy({ it.first }, { it.second })
            .map { (tier, counts) -> SolvedByTier(tier, counts.sum()) }
            .sortedBy { it.tier.ordinal }

    /**
     * 태그별 푼 문제 수 (#232). 난이도 분포와 같은 자리에 놓인다.
     *
     * **태그가 없는 문제는 어디에도 세지 않는다.** 합계가 푼 문제 수와 다를 수 있는데,
     * 그것이 사실이다 — "분류가 붙은 문제 중에서" 의 분포다.
     */
    fun solvedByTag(userId: Long): List<SolvedByTag> =
        jdbcClient.sql(
            """
            SELECT t.slug, t.name, count(DISTINCT p.id) AS solved
            FROM submissions s
            JOIN problems p ON p.id = s.problem_id
            JOIN problem_tags pt ON pt.problem_id = p.id
            JOIN tags t ON t.id = pt.tag_id
            WHERE s.user_id = :userId
              AND s.deleted_at IS NULL
              AND s.kind = 'USER'
              AND s.verdict = 'ACCEPTED'
              AND p.deleted_at IS NULL
            GROUP BY t.slug, t.name
            ORDER BY solved DESC, t.name
            """,
        )
            .param("userId", userId)
            .query { rs, _ -> SolvedByTag(rs.getString("slug"), rs.getString("name"), rs.getInt("solved")) }
            .list()
}

/** 태그별 푼 문제 수 (#232). */
data class SolvedByTag(val slug: String, val name: String, val solved: Int)
