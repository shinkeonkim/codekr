package codekr.api.ranking.badge

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class BadgeRepository(private val jdbcClient: JdbcClient) {

    /**
     * 뱃지를 준다. 이미 있으면 아무 일도 없다.
     *
     * **한 번 준 뱃지는 회수하지 않는다.** 그때 실제로 한 일이고, 나중에 조건이 깨져도
     * (재채점으로 정답이 뒤집혀도) 그날 그 일을 한 것은 사실이다. 회수하면 화면에서
     * 뱃지가 사라지는데, 사용자는 그 이유를 알 길이 없다.
     */
    fun award(userId: Long, code: String): Boolean =
        jdbcClient.sql(
            """
            INSERT INTO user_badges (user_id, code) VALUES (:userId, :code)
            ON CONFLICT (user_id, code) DO NOTHING
            """,
        )
            .param("userId", userId)
            .param("code", code)
            .update() > 0

    fun findAll(userId: Long): List<AwardedBadge> =
        jdbcClient.sql("SELECT code, awarded_at FROM user_badges WHERE user_id = :userId ORDER BY awarded_at DESC")
            .param("userId", userId)
            .query { rs, _ ->
                val info = Badge.describe(rs.getString("code"))
                AwardedBadge(info.code, info.label, info.description, rs.getTimestamp("awarded_at").toInstant())
            }
            .list()
}

data class AwardedBadge(val code: String, val label: String, val description: String, val awardedAt: Instant)
