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
     * ## 회수는 **규칙이 바뀐 경우에만** 한다 (#558)
     *
     * 원래는 회수를 아예 하지 않기로 했었다 (#41) — "그때 실제로 한 일이고, 나중에
     * 조건이 깨져도 그날 그 일을 한 것은 사실" 이기 때문이다. 그 판단은
     * **재채점으로 정답이 뒤집힌 경우**에는 그대로다. 사용자가 아무것도 안 했는데
     * 뱃지가 사라지는 것이라 성격이 더 나쁘다.
     *
     * 다만 **운영자가 규칙을 좁힌 경우**는 다르다. 그대로 두면 "자격 없는 사람이
     * 가진 뱃지" 가 남고, 규칙이 무엇을 뜻하는지가 흐려진다. 그래서 그 경우에만
     * [revoke] 로 거둔다.
     *
     * **회수하면 반드시 알린다.** 원래 반대 근거가 "사용자는 그 이유를 알 길이 없다"
     * 였으므로, 알리지 않으면 뒤집을 이유가 없어진다.
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

    /**
     * 뱃지를 거둔다 (#558). 없으면 아무 일도 없다.
     *
     * **부르는 곳은 규칙 수정 하나뿐이다** — 다른 곳에서 부르기 시작하면 위의 판단
     * ("재채점으로는 거두지 않는다")이 조용히 무너진다.
     */
    fun revoke(userId: Long, code: String): Boolean =
        jdbcClient.sql("DELETE FROM user_badges WHERE user_id = :userId AND code = :code")
            .param("userId", userId)
            .param("code", code)
            .update() > 0

    /** 그 뱃지를 가진 사람들 (#558). 회수 대상을 고르는 데 쓴다. */
    fun holdersOf(code: String): List<Long> =
        jdbcClient.sql("SELECT user_id FROM user_badges WHERE code = :code")
            .param("code", code)
            .query { rs, _ -> rs.getLong("user_id") }
            .list()

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
