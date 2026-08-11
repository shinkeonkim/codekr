package codekr.api.ranking.repository

import codekr.api.ranking.entity.SCORE_PROBLEM_LIMIT
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 사용자별 해결 문제 점수 (#57).
 *
 * **증분이 아니라 원자료에서 다시 세어 덮어쓴다** — 활동 집계(#105)와 같은 이유다.
 * 재채점(#107)으로 정답이 오답이 되면 점수는 **내려가야 한다**. 증분 방식이면
 * 내려갈 길이 없어서 잘못된 점수가 영구히 남는다.
 */
@Repository
class UserProblemScoreRepository(private val jdbcClient: JdbcClient) {

    /**
     * 한 사용자·한 문제의 점수를 제출 기록에서 다시 계산한다.
     *
     * @return 점수가 달라졌으면 그 변화량. 그대로면 0.
     */
    fun refresh(userId: Long, problemId: Long): Int {
        val before = scoreOf(userId, problemId)
        val updated = jdbcClient.sql(
            """
            INSERT INTO user_problem_scores (user_id, problem_id, score, solved_at)
            SELECT s.user_id, s.problem_id,
                   -- round(10 * 1.25^(레벨-1)). 맞힌 시점이 아니라 '지금'의 난이도를 쓴다 —
                   -- 난이도가 조정되면 모두의 점수가 같은 기준으로 움직여야 공평하다.
                   round(10 * power(1.25, p.difficulty_level - 1))::int,
                   min(s.created_at)
            FROM submissions s
            JOIN problems p ON p.id = s.problem_id
            WHERE s.user_id = :userId
              AND s.problem_id = :problemId
              AND s.verdict = 'ACCEPTED'
              AND s.kind = 'USER'
              AND s.deleted_at IS NULL
              AND p.deleted_at IS NULL
              AND p.published = true
            GROUP BY s.user_id, s.problem_id, p.difficulty_level
            ON CONFLICT (user_id, problem_id)
            DO UPDATE SET score = excluded.score, solved_at = excluded.solved_at
            """,
        )
            .param("userId", userId)
            .param("problemId", problemId)
            .update()

        // 맞힌 기록이 하나도 남지 않았으면 행을 지운다. 남기면 없는 점수가 계속 잡힌다.
        if (updated == 0) deleteOne(userId, problemId)

        return scoreOf(userId, problemId) - before
    }

    /** 그 사용자의 모든 문제를 다시 계산한다. 난이도 조정·데이터 보정 후의 복구 경로다. */
    fun recomputeAll(userId: Long) {
        jdbcClient.sql("DELETE FROM user_problem_scores WHERE user_id = :userId")
            .param("userId", userId)
            .update()
        jdbcClient.sql(
            """
            INSERT INTO user_problem_scores (user_id, problem_id, score, solved_at)
            SELECT s.user_id, s.problem_id,
                   round(10 * power(1.25, p.difficulty_level - 1))::int,
                   min(s.created_at)
            FROM submissions s
            JOIN problems p ON p.id = s.problem_id
            WHERE s.user_id = :userId
              AND s.verdict = 'ACCEPTED'
              AND s.kind = 'USER'
              AND s.deleted_at IS NULL
              AND p.deleted_at IS NULL
              AND p.published = true
            GROUP BY s.user_id, s.problem_id, p.difficulty_level
            """,
        )
            .param("userId", userId)
            .update()
    }

    /** 그 사용자가 맞힌 문제들. 뱃지를 다시 맞출 때 문제마다 조건을 확인해야 한다 (#177). */
    fun solvedProblemIds(userId: Long): List<Long> =
        jdbcClient.sql("SELECT problem_id FROM user_problem_scores WHERE user_id = :userId ORDER BY solved_at")
            .param("userId", userId)
            .query { rs, _ -> rs.getLong("problem_id") }
            .list()

    /**
     * 점수가 잡혀야 할 사용자들 (#177).
     *
     * **점수 표가 아니라 제출에서 찾는다.** 표에서 찾으면 아직 한 번도 반영되지 않은
     * 사용자가 빠지는데, 기능 도입 직후에는 그쪽이 전부다.
     */
    fun userIdsWithAcceptedSubmissions(): List<Long> =
        jdbcClient.sql(
            """
            SELECT DISTINCT s.user_id
            FROM submissions s
            JOIN problems p ON p.id = s.problem_id
            WHERE s.verdict = 'ACCEPTED'
              AND s.kind = 'USER'
              AND s.deleted_at IS NULL
              AND p.deleted_at IS NULL
              AND p.published = true
            """,
        )
            .query { rs, _ -> rs.getLong("user_id") }
            .list()

    /** 그 사용자의 지표 값. 랭킹 목록과 같은 규칙(상위 N개)으로 계산한다. */
    fun totalsOf(userId: Long): Pair<Int, Int> =
        jdbcClient.sql(
            """
            SELECT coalesce(sum(score) FILTER (WHERE rn <= :scoreLimit), 0)::int AS score,
                   count(*)::int                                                 AS solved_count
            FROM (
                SELECT score,
                       row_number() OVER (ORDER BY score DESC, problem_id) AS rn
                FROM user_problem_scores
                WHERE user_id = :userId
            ) ranked
            """,
        )
            .param("userId", userId)
            .param("scoreLimit", SCORE_PROBLEM_LIMIT)
            .query { rs, _ -> rs.getInt("score") to rs.getInt("solved_count") }
            .single()

    private fun scoreOf(userId: Long, problemId: Long): Int =
        jdbcClient.sql(
            "SELECT coalesce(max(score), 0) FROM user_problem_scores WHERE user_id = :userId AND problem_id = :problemId",
        )
            .param("userId", userId)
            .param("problemId", problemId)
            .query(Int::class.java)
            .single()

    private fun deleteOne(userId: Long, problemId: Long) {
        jdbcClient.sql("DELETE FROM user_problem_scores WHERE user_id = :userId AND problem_id = :problemId")
            .param("userId", userId)
            .param("problemId", problemId)
            .update()
    }
}
