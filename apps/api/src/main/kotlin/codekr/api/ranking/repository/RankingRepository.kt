package codekr.api.ranking.repository

import codekr.api.ranking.dto.RankingEntry
import codekr.api.ranking.entity.RankingMetric
import codekr.api.ranking.entity.RankingPeriod
import codekr.api.ranking.entity.SCORE_PROBLEM_LIMIT
import codekr.api.user.entity.UserRole
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
    ): List<RankingEntry> =
        jdbcClient.sql(rankingSql(metric, period))
            .param("scoreLimit", SCORE_PROBLEM_LIMIT)
            .param("limit", limit)
            .param("offset", offset)
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

    fun countRanked(period: RankingPeriod): Int =
        jdbcClient.sql(
            """
            SELECT count(DISTINCT s.user_id)
            FROM user_problem_scores s
            JOIN users u ON u.id = s.user_id
            WHERE $RANKED_USER_FILTER AND ${periodFilter(period)}
            """,
        )
            .query(Int::class.java)
            .single()

    /** 그 사용자의 순위. 푼 문제가 하나도 없으면 null 이다 — 0등이 아니라 순위가 없는 것이다. */
    fun findRankOf(nickname: String, metric: RankingMetric, period: RankingPeriod): RankingEntry? =
        jdbcClient.sql(
            """
            SELECT * FROM (${rankingSql(metric, period, paged = false)}) ranked
            WHERE nickname = :nickname
            """,
        )
            .param("scoreLimit", SCORE_PROBLEM_LIMIT)
            .param("nickname", nickname)
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
     * 지표에 따라 정렬만 달라진다. 집계는 같다 —
     * 두 지표가 서로 다른 질의를 쓰면 같은 사람의 값이 화면마다 달라질 수 있다.
     *
     * 동점 처리: 총점 → 푼 문제 수 → **최초 해결이 이른 순** → 닉네임.
     */
    /** 월간은 '이번 달에 처음 맞힌 문제'만 본다. 지난달 점수를 이월하면 월간이 아니다. */
    private fun periodFilter(period: RankingPeriod): String = when (period) {
        RankingPeriod.ALL_TIME -> "true"
        RankingPeriod.MONTHLY -> "s.solved_at >= date_trunc('month', now())"
    }

    private fun rankingSql(metric: RankingMetric, period: RankingPeriod, paged: Boolean = true): String {
        val order = when (metric) {
            RankingMetric.SCORE -> "score DESC, solved_count DESC, last_solved_at ASC, nickname ASC"
            RankingMetric.SOLVED_COUNT -> "solved_count DESC, score DESC, last_solved_at ASC, nickname ASC"
        }
        val paging = if (paged) "LIMIT :limit OFFSET :offset" else ""

        return """
            SELECT rank() OVER (ORDER BY $order) AS rank,
                   nickname, score, solved_count, last_solved_at
            FROM (
                SELECT u.nickname,
                       -- 상위 N개만 합산한다 (#85). 전부 더하면 쉬운 문제를 무한히 풀어 이긴다.
                       coalesce(sum(s.score) FILTER (WHERE s.rn <= :scoreLimit), 0)::int AS score,
                       count(*)::int                                                     AS solved_count,
                       max(s.solved_at)                                                  AS last_solved_at
                FROM (
                    SELECT ups.*,
                           row_number() OVER (PARTITION BY ups.user_id ORDER BY ups.score DESC, ups.problem_id) AS rn
                    FROM user_problem_scores ups
                ) s
                JOIN users u ON u.id = s.user_id
                -- 랭킹에 이름이 오르는 것을 원하지 않는 사람은 뺀다 (#58).
                WHERE $RANKED_USER_FILTER AND ${periodFilter(period)}
                GROUP BY u.id, u.nickname
            ) totals
            ORDER BY $order
            $paging
        """.trimIndent()
    }
}

/**
 * 랭킹에 올라갈 사람의 조건 (#188).
 *
 * **관리 권한을 가진 계정은 제외한다.** 출제자는 정답을 이미 알고 있고, 문제 검증 과정에서
 * 정답 제출이 저절로 쌓인다. 같은 표에 놓으면 랭킹의 뜻이 흐려진다.
 *
 * 역할이 하나라도 관리 영역(`UserRole.ADMIN_AREA`)에 들면 제외한다 — 화면에서 어드민을
 * 볼 수 있는 사람과 같은 기준이다. 기준이 두 벌이면 "어드민인데 랭킹에는 있는" 상태가 생긴다.
 *
 * 조건을 문자열 상수로 두는 이유: 세 질의(목록·건수·내 순위)가 **같은 조건**을 써야 한다.
 * 한 곳만 고치면 목록에는 없는데 "내 순위"에는 있는 사람이 생긴다.
 */
private val RANKED_USER_FILTER =
    """
    u.ranking_opt_out = false
      AND NOT EXISTS (
          SELECT 1 FROM user_roles r
          WHERE r.user_id = u.id AND r.role IN (${UserRole.ADMIN_AREA.joinToString { "'" + it.name + "'" }})
      )
    """.trimIndent()
