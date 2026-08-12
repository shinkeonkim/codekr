package codekr.api.ranking.repository

import codekr.api.ranking.entity.ProblemScore
import codekr.api.ranking.entity.SCORE_PROBLEM_LIMIT
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 문제 하나에 걸린 점수 행 전체를 다시 맞춘다 (#194).
 *
 * **사용자별이 아니라 문제별이다.** `UserProblemScoreRepository` 는 (사용자 × 문제)나
 * 사용자 한 명을 단위로 본다. 난이도가 바뀌면 그 문제를 맞힌 **모든 사람**이 함께
 * 움직여야 하는데, 그것을 사용자 목록을 돌며 한 명씩 처리하면 어드민의 저장 버튼이
 * 사람 수만큼 느려진다. 여기서는 문장 세 개로 끝난다 — 몇 명이 풀었든 같은 비용이다.
 */
@Repository
class ProblemScoreResyncRepository(private val jdbcClient: JdbcClient) {

    /**
     * 자격을 잃은 점수 행을 지운다.
     *
     * 문제가 비공개가 되거나 지워지면 점수가 빠져야 한다 — 점수 계산식이 처음부터
     * `p.published = true` 를 보고 있었다. 그동안은 **그 조건을 다시 확인하는 경로가
     * 없어서** 내려간 적이 없다.
     *
     * @return 점수가 빠진 사용자들.
     */
    fun deleteDisqualified(problemId: Long): List<Long> =
        jdbcClient.sql(
            """
            DELETE FROM user_problem_scores ups
            WHERE ups.problem_id = :problemId
              AND NOT EXISTS (
                  SELECT 1
                  FROM submissions s
                  JOIN problems p ON p.id = s.problem_id
                  WHERE s.user_id = ups.user_id
                    AND s.problem_id = ups.problem_id
                    AND s.verdict = 'ACCEPTED'
                    AND s.kind = 'USER'
                    AND s.deleted_at IS NULL
                    AND p.deleted_at IS NULL
                    AND p.published = true
              )
            RETURNING ups.user_id
            """,
        )
            .param("problemId", problemId)
            .query { rs, _ -> rs.getLong("user_id") }
            .list()

    /**
     * 지금 난이도로 점수를 다시 쓴다. 자격이 되살아난 사람(다시 공개된 문제)도 여기서 들어온다.
     *
     * **값이 그대로인 행은 건드리지 않는다.** 그래야 돌려준 사용자 목록이 "실제로 점수가
     * 움직인 사람" 이 되고, 뒤따르는 최고 점수 갱신이 헛돌지 않는다.
     *
     * @return 점수가 달라진 사용자들.
     */
    fun upsertQualified(problemId: Long): List<Long> =
        jdbcClient.sql(
            """
            INSERT INTO user_problem_scores (user_id, problem_id, score, solved_at)
            SELECT s.user_id, s.problem_id, ${ProblemScore.SQL}, min(s.created_at)
            FROM submissions s
            JOIN problems p ON p.id = s.problem_id
            WHERE s.problem_id = :problemId
              AND s.verdict = 'ACCEPTED'
              AND s.kind = 'USER'
              AND s.deleted_at IS NULL
              AND p.deleted_at IS NULL
              AND p.published = true
            GROUP BY s.user_id, s.problem_id, p.difficulty_level
            ON CONFLICT (user_id, problem_id) DO UPDATE
                SET score = excluded.score, solved_at = excluded.solved_at
                WHERE user_problem_scores.score IS DISTINCT FROM excluded.score
                   OR user_problem_scores.solved_at IS DISTINCT FROM excluded.solved_at
            RETURNING user_id
            """,
        )
            .param("problemId", problemId)
            .query { rs, _ -> rs.getLong("user_id") }
            .list()

    /**
     * 최고 점수를 다시 맞춘다.
     *
     * **올리기만 한다.** 난이도가 내려가 점수가 줄어도 최고 점수는 그대로다 — 실력 티어에
     * 강등을 두지 않기로 한 것과 같은 규칙이다 (#58). 티어가 내려가는 순간을 만들면,
     * 그 원인이 자기 제출이 아니라 **어드민의 난이도 조정**인 경우가 생긴다.
     *
     * 랭킹 목록의 점수는 이 값이 아니라 점수 행에서 그때그때 더하므로, 그쪽은 오르내림이
     * 모두 그대로 보인다.
     */
    fun raisePeakScores(userIds: Collection<Long>) {
        if (userIds.isEmpty()) return
        jdbcClient.sql(
            """
            WITH totals AS (
                SELECT user_id, coalesce(sum(score) FILTER (WHERE rn <= :scoreLimit), 0)::int AS score
                FROM (
                    SELECT user_id, score,
                           row_number() OVER (PARTITION BY user_id ORDER BY score DESC, problem_id) AS rn
                    FROM user_problem_scores
                    WHERE user_id IN (:userIds)
                ) ranked
                GROUP BY user_id
            )
            UPDATE users u
            SET peak_score = totals.score
            FROM totals
            WHERE u.id = totals.user_id
              AND totals.score > u.peak_score
            """,
        )
            .param("scoreLimit", SCORE_PROBLEM_LIMIT)
            .param("userIds", userIds)
            .update()
    }
}
