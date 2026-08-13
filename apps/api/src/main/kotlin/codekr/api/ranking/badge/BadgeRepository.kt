package codekr.api.ranking.badge

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class BadgeRepository(
    private val catalog: BadgeCatalog,private val jdbcClient: JdbcClient) {

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

    /** 이미 가진 코드들. **지표를 계산하기 전에 걸러내는 데 쓴다** (#202). */
    fun codesOf(userId: Long): Set<String> =
        jdbcClient.sql("SELECT code FROM user_badges WHERE user_id = :userId")
            .param("userId", userId)
            .query { rs, _ -> rs.getString("code") }
            .list()
            .toSet()

    /**
     * 그 사람이 받은 뱃지 (#58).
     *
     * **정의를 표에서 읽는다** (#201) — 이름·설명이 배포 없이 바뀌고, 숨긴 것은 빠지고,
     * 순서는 정의가 정한다. 정의가 없는 옛 코드도 죽지 않는다.
     */
    fun findAll(userId: Long): List<AwardedBadge> =
        jdbcClient.sql("SELECT code, awarded_at FROM user_badges WHERE user_id = :userId")
            .param("userId", userId)
            .query { rs, _ ->
                val code = rs.getString("code")
                val info = catalog.describe(code)
                AwardedBadge(info.code, info.label, info.description, rs.getTimestamp("awarded_at").toInstant())
            }
            .list()
            // 숨긴 뱃지는 화면에서 빠진다. **지운 것이 아니다** — 다시 켜면 돌아온다.
            .filter { catalog.isVisible(it.code) }
            .sortedWith(compareBy({ catalog.sortOrderOf(it.code) }, { it.code }))
}

data class AwardedBadge(val code: String, val label: String, val description: String, val awardedAt: Instant)
