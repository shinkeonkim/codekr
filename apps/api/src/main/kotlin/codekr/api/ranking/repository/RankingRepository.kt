package codekr.api.ranking.repository

import codekr.api.ranking.dto.RankingEntry
import codekr.api.ranking.entity.RankingMetric
import codekr.api.ranking.entity.RankingPeriod
import codekr.api.ranking.entity.SCORE_PROBLEM_LIMIT
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * 랭킹 조회 (#57, #85).
 *
 * `user_problem_scores` 는 **사용자 × 푼 문제 = 1행**이다 (제출 수와 무관).
 * 그 위에서 집계하므로 별도의 순위 표를 두지 않는다 — 두면 원자료와 갈라지고,
 * 갈라진 것을 되돌리는 장치를 또 만들어야 한다 (#105 에서 배운 대가).
 */
@Repository
class RankingRepository(private val jdbcClient: JdbcClient) {

    fun findPage(
        metric: RankingMetric,
        period: RankingPeriod,
        limit: Int,
        offset: Int,
        affiliationId: Long? = null,
    ): List<RankingEntry> =
        jdbcClient.sql(rankingSql(metric, period, affiliationId = affiliationId))
            .param("scoreLimit", SCORE_PROBLEM_LIMIT)
            .param("limit", limit)
            .param("offset", offset)
            .param("affiliationId", affiliationId)
            .query { rs, _ ->
                RankingEntry(
                    rank = rs.getInt("rank"),
                    nickname = rs.getString("nickname"),
                    score = rs.getInt("score"),
                    solvedCount = rs.getInt("solved_count"),
                    lastSolvedAt = rs.getTimestamp("last_solved_at")?.toInstant(),
                )
            }
            .list()

    /**
     * 순위표에 오르는 사람 수 (#391).
     *
     * **기간과 무관하다.** 아직 못 푼 사람도 목록에 있으므로, 이번 달 랭킹의 인원도
     * 가입자 수와 같다. 전에는 "그 기간에 푼 사람" 만 셌다.
     */
    fun countRanked(period: RankingPeriod, affiliationId: Long? = null): Int =
        jdbcClient.sql(
            """
            SELECT count(*) FROM users u
            WHERE $RANKED_USER_FILTER AND ${affiliationCondition(affiliationId)}
            """,
        )
            .param("affiliationId", affiliationId)
            .query(Int::class.java)
            .single()

    /** 그 사용자의 순위. 랭킹에 낄 자격이 있으면 **0점이어도 순위가 있다** (#391). */
    fun findRankOf(
        nickname: String,
        metric: RankingMetric,
        period: RankingPeriod,
        affiliationId: Long? = null,
    ): RankingEntry? =
        jdbcClient.sql(
            """
            SELECT * FROM (${rankingSql(metric, period, paged = false, affiliationId = affiliationId)}) ranked
            WHERE nickname = :nickname
            """,
        )
            .param("scoreLimit", SCORE_PROBLEM_LIMIT)
            .param("nickname", nickname)
            .param("affiliationId", affiliationId)
            .query { rs, _ ->
                RankingEntry(
                    rank = rs.getInt("rank"),
                    nickname = rs.getString("nickname"),
                    score = rs.getInt("score"),
                    solvedCount = rs.getInt("solved_count"),
                    lastSolvedAt = rs.getTimestamp("last_solved_at")?.toInstant(),
                )
            }
            .optional()
            .orElse(null)

    /**
     * 월간은 '이번 달에 처음 맞힌 문제'만 본다. 지난달 점수를 이월하면 월간이 아니다.
     *
     * **`WHERE` 가 아니라 `JOIN` 조건에 붙는다** (#391). `WHERE` 에 두면 왼쪽 조인이
     * 안쪽 조인이 되어, 이번 달에 안 푼 사람이 통째로 사라진다 — 고치려던 바로 그것이다.
     */
    private fun periodFilter(period: RankingPeriod): String = when (period) {
        RankingPeriod.ALL_TIME -> "true"
        RankingPeriod.MONTHLY -> "s.solved_at >= date_trunc('month', now())"
    }

    /**
     * 지표에 따라 정렬만 달라진다. 집계는 같다 —
     * 두 지표가 서로 다른 질의를 쓰면 같은 사람의 값이 화면마다 달라질 수 있다.
     *
     * **`users` 에서 시작한다** (#391). 전에는 `user_problem_scores` 에서 시작했는데,
     * 그 표는 **사용자 × 푼 문제 = 1행**이라 푼 문제가 없으면 행이 없고, 행이 없으면
     * 순위표에 없었다. #207 이 "일부만 보이는 순위표는 순위가 아니다" 며 비참여 설정을
     * 걷어냈는데, **0점인 사람은 설정이 아니라 집계 구조로 빠지고 있었다.**
     *
     * 동점 처리: 총점 → 푼 문제 수 → 최초 해결이 이른 순 → **가입이 이른 순**.
     *
     * 마지막이 닉네임이 아닌 이유가 둘이다.
     *   1. 0점이 대다수가 되는데, 그들끼리는 `last_solved_at` 이 없어 **사실상 가나다순**
     *      으로 줄 세운 순위표가 된다
     *   2. **닉네임은 이제 바뀐다** (#307). 바뀌는 값으로 등수를 가르면 이름을 고친
     *      사람 때문에 남의 등수가 함께 움직인다. 가입일은 바뀌지 않는다
     */
    /**
     * 그 소속 사람들만 (#399).
     *
     * **모집단을 좁히는 것이지 정렬을 바꾸는 것이 아니다.** 그래서 등수는 그 안에서
     * 1위부터 다시 매겨진다 — "우리 학교에서 3등" 이 이 기능의 이유다.
     *
     * `null` 이면 조건이 `true` 다. 조건을 문자열로 끼우되 **값은 바인딩한다** —
     * 소속 id 는 사람이 보내는 값이다.
     */
    private fun affiliationCondition(affiliationId: Long?): String =
        if (affiliationId == null) {
            "true"
        } else {
            "EXISTS (SELECT 1 FROM user_affiliations ua " +
                "WHERE ua.user_id = u.id AND ua.affiliation_id = :affiliationId)"
        }

    private fun rankingSql(
        metric: RankingMetric,
        period: RankingPeriod,
        paged: Boolean = true,
        affiliationId: Long? = null,
    ): String {
        val tieBreak = "last_solved_at ASC NULLS LAST, created_at ASC"
        val order = when (metric) {
            RankingMetric.SCORE -> "score DESC, solved_count DESC, $tieBreak"
            RankingMetric.SOLVED_COUNT -> "solved_count DESC, score DESC, $tieBreak"
        }
        val paging = if (paged) "LIMIT :limit OFFSET :offset" else ""

        return """
            SELECT rank() OVER (ORDER BY $order) AS rank,
                   nickname, score, solved_count, last_solved_at
            FROM (
                SELECT u.nickname, u.created_at,
                       -- 상위 N개만 합산한다 (#85). 전부 더하면 쉬운 문제를 무한히 풀어 이긴다.
                       coalesce(sum(s.score) FILTER (WHERE s.rn <= :scoreLimit), 0)::int AS score,
                       -- **`count(*)` 가 아니다.** 왼쪽 조인이라 푼 문제가 없어도 행이 하나
                       -- 남는데, `count(*)` 면 그 사람이 1문제 푼 것으로 세어진다.
                       count(s.user_id)::int                                             AS solved_count,
                       max(s.solved_at)                                                  AS last_solved_at
                FROM users u
                LEFT JOIN (
                    SELECT ups.*,
                           row_number() OVER (PARTITION BY ups.user_id ORDER BY ups.score DESC, ups.problem_id) AS rn
                    FROM user_problem_scores ups
                ) s ON s.user_id = u.id AND ${periodFilter(period)}
                WHERE $RANKED_USER_FILTER AND ${affiliationCondition(affiliationId)}
                GROUP BY u.id, u.nickname, u.created_at
            ) totals
            ORDER BY $order
            $paging
        """.trimIndent()
    }
}

/**
 * 랭킹에 올라갈 사람의 조건 (#207).
 *
 * **탈퇴한 사람만 뺀다.** 없는 사람이 순위에 있으면 눌렀을 때 갈 곳이 없다.
 *
 * 전에는 비참여 설정(#41)과 어드민(#188)도 함께 걸렀다. 둘 다 걷어냈다 —
 * **빠진 사람이 있는 순위에서 "3위" 는 무엇의 3위인지 알 수 없다.** 어드민이 정답을 보고
 * 푸는 것이 문제라면 그것은 운영 규율의 문제이지 순위표를 접을 이유가 아니다.
 *
 * 조건을 문자열 상수로 두는 이유: 세 질의(목록·건수·내 순위)가 **같은 조건**을 써야 한다.
 * 한 곳만 고치면 목록에는 없는데 "내 순위"에는 있는 사람이 생긴다.
 */
private val RANKED_USER_FILTER = "u.withdrawn_at IS NULL"
