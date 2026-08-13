package codekr.api.ranking.repository

import codekr.api.affiliation.entity.AffiliationKind
import codekr.api.ranking.dto.AffiliationRankingEntry
import codekr.api.ranking.entity.MIN_AFFILIATION_MEMBERS
import codekr.api.ranking.entity.RankingPeriod
import codekr.api.ranking.entity.SCORE_PROBLEM_LIMIT
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 소속끼리 겨루는 랭킹 (#400, #240 5단계).
 *
 * **값은 상위 [MIN_AFFILIATION_MEMBERS] 명의 점수 합이다** (기획서 6절).
 *
 *  - **합**이면 사람 많은 곳이 언제나 이긴다. 200명 학교가 5명 학교를 이기는 것은 시시하다
 *  - **평균**이면 소수 정예가 이기고, **잘하는 사람만 남기고 내보내는 유인**이 생긴다.
 *    이쪽이 훨씬 나쁘다 — 순위표가 사람을 밀어내는 도구가 된다
 *
 * 상위 N명 합은 둘 다 피한다. 6번째 사람이 들어와도 순위가 안 바뀌므로 **끌어들일
 * 이유도 내보낼 이유도 없다.**
 */
@Repository
class AffiliationRankingRepository(private val jdbcClient: JdbcClient) {

    fun findPage(period: RankingPeriod, limit: Int, offset: Int): List<AffiliationRankingEntry> =
        jdbcClient.sql("${rankingSql(period)}\nLIMIT :limit OFFSET :offset")
            .param("scoreLimit", SCORE_PROBLEM_LIMIT)
            .param("topN", MIN_AFFILIATION_MEMBERS)
            .param("minMembers", MIN_AFFILIATION_MEMBERS)
            .param("limit", limit)
            .param("offset", offset)
            .query { rs, _ ->
                AffiliationRankingEntry(
                    rank = rs.getInt("rank"),
                    affiliationId = rs.getLong("id"),
                    name = rs.getString("name"),
                    kindLabel = AffiliationKind.valueOf(rs.getString("kind")).label,
                    score = rs.getInt("score"),
                    memberCount = rs.getInt("member_count"),
                )
            }
            .list()

    /** 순위표에 오르는 소속 수. **최소 인원을 못 넘는 곳은 세지 않는다.** */
    fun countRanked(period: RankingPeriod): Int =
        jdbcClient.sql("SELECT count(*) FROM (${rankingSql(period)}) ranked")
            .param("scoreLimit", SCORE_PROBLEM_LIMIT)
            .param("topN", MIN_AFFILIATION_MEMBERS)
            .param("minMembers", MIN_AFFILIATION_MEMBERS)
            .query(Int::class.java)
            .single()

    /**
     * 월간은 '이번 달에 처음 맞힌 문제'만 본다 — 사람 랭킹과 같은 규칙이다.
     *
     * **`JOIN` 조건에 붙는다.** `WHERE` 에 두면 왼쪽 조인이 안쪽 조인이 되어, 이번 달에
     * 아무도 안 푼 소속이 통째로 사라진다 (#391 에서 사람 랭킹이 겪은 것과 같다).
     */
    private fun periodFilter(period: RankingPeriod): String = when (period) {
        RankingPeriod.ALL_TIME -> "true"
        RankingPeriod.MONTHLY -> "s.solved_at >= date_trunc('month', now())"
    }

    /**
     * 동점이면 **먼저 등록된 소속이 위다.**
     *
     * 인원으로 가르지 않는다 — 인원을 순위에 넣는 순간 "사람을 더 넣어라" 또는
     * "덜어내라" 가 되고, 그것이 바로 이 점수 방식이 피하려던 것이다.
     */
    private fun rankingSql(period: RankingPeriod): String =
        """
        SELECT rank() OVER (ORDER BY score DESC, id ASC) AS rank, id, name, kind, score, member_count
        FROM (
            SELECT a.id, a.name, a.kind,
                   sum(m.score) FILTER (WHERE m.rn <= :topN)::int AS score,
                   max(m.member_count)::int                       AS member_count
            FROM (
                SELECT affiliation_id, score,
                       row_number() OVER (PARTITION BY affiliation_id ORDER BY score DESC, user_id) AS rn,
                       count(*)     OVER (PARTITION BY affiliation_id)                              AS member_count
                FROM (
                    SELECT ua.affiliation_id, u.id AS user_id,
                           coalesce(sum(s.score) FILTER (WHERE s.rn <= :scoreLimit), 0)::int AS score
                    FROM user_affiliations ua
                    -- 탈퇴한 사람은 사람 랭킹에서 빠진다 (#207). 여기서도 세지 않는다 —
                    -- 없는 사람의 점수가 학교 점수에 남으면 그 숫자는 아무 말도 하지 않는다.
                    JOIN users u ON u.id = ua.user_id AND u.withdrawn_at IS NULL
                    LEFT JOIN (
                        SELECT ups.*,
                               row_number() OVER (
                                   PARTITION BY ups.user_id ORDER BY ups.score DESC, ups.problem_id
                               ) AS rn
                        FROM user_problem_scores ups
                    ) s ON s.user_id = u.id AND ${periodFilter(period)}
                    GROUP BY ua.affiliation_id, u.id
                ) member_scores
            ) m
            -- 내려간 소속은 겨루지 않는다. 도메인이 이미 떨어져 새로 붙을 사람도 없다 (#397).
            JOIN affiliations a ON a.id = m.affiliation_id AND a.deleted_at IS NULL
            GROUP BY a.id, a.name, a.kind
            -- **한 명짜리 학교가 1등으로 올라오면 그 순위표는 아무 말도 하지 않는다** (기획서 6절).
            HAVING max(m.member_count) >= :minMembers
        ) totals
        ORDER BY score DESC, id ASC
        """.trimIndent()
}
