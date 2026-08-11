package codekr.api.contest.scoreboard

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * 대회 순위표의 원자료 (#63).
 *
 * 참가자 × 문제마다 **첫 정답 시각**과 **시도 횟수**를 센다. 순위 계산에 필요한 것은
 * 그 둘뿐이다 — 나머지는 애플리케이션에서 조립한다.
 */
@Repository
class ScoreboardRepository(private val jdbcClient: JdbcClient) {

    /**
     * @param cutoff 이 시각 이후의 제출은 **결과를 세지 않는다** (동결, #86).
     *   null 이면 전부 센다. 시도했다는 사실은 동결해도 감추지 않으므로 횟수는 늘 전부 센다.
     */
    fun cells(contestId: Long, cutoff: Instant?): List<ScoreboardCell> =
        jdbcClient.sql(
            """
            SELECT s.user_id,
                   s.problem_id,
                   count(*)::int                                                      AS attempts,
                   count(*) FILTER (
                       WHERE :cutoff::timestamptz IS NOT NULL AND s.created_at >= :cutoff::timestamptz
                   )::int                                                             AS pending,
                   min(s.created_at) FILTER (
                       WHERE s.verdict = 'ACCEPTED'
                         AND (:cutoff::timestamptz IS NULL OR s.created_at < :cutoff::timestamptz)
                   )                                                                  AS solved_at
            FROM submissions s
            WHERE s.contest_id = :contestId
              AND s.deleted_at IS NULL
            GROUP BY s.user_id, s.problem_id
            """,
        )
            .param("contestId", contestId)
            .param("cutoff", cutoff?.toString())
            .query { rs, _ ->
                ScoreboardCell(
                    userId = rs.getLong("user_id"),
                    problemId = rs.getLong("problem_id"),
                    attempts = rs.getInt("attempts"),
                    pending = rs.getInt("pending"),
                    solvedAt = rs.getTimestamp("solved_at")?.toInstant(),
                )
            }
            .list()

    /** 참가자와 등록 시각. 동점 처리의 세 번째 키다. */
    fun participants(contestId: Long): List<ScoreboardParticipant> =
        jdbcClient.sql(
            """
            SELECT r.user_id, u.nickname, r.registered_at
            FROM contest_registrations r
            JOIN users u ON u.id = r.user_id
            WHERE r.contest_id = :contestId
            """,
        )
            .param("contestId", contestId)
            .query { rs, _ ->
                ScoreboardParticipant(
                    userId = rs.getLong("user_id"),
                    nickname = rs.getString("nickname"),
                    registeredAt = rs.getTimestamp("registered_at").toInstant(),
                )
            }
            .list()

    /**
     * 이 대회에 재채점이 도는 중인가 (#63).
     *
     * **중간 상태의 순위를 보여주면 참가자가 잘못된 정보로 판단한다.** 화면이 그 사실을
     * 알려야 한다.
     */
    fun rejudgeInProgress(contestId: Long): Boolean =
        jdbcClient.sql(
            """
            SELECT EXISTS (
                SELECT 1 FROM submissions
                WHERE contest_id = :contestId AND rejudge_batch_id IS NOT NULL AND deleted_at IS NULL
            )
            """,
        )
            .param("contestId", contestId)
            .query(Boolean::class.java)
            .single()
}

data class ScoreboardCell(
    val userId: Long,
    val problemId: Long,
    val attempts: Int,
    /** 동결 이후의 제출 수. 결과는 감춰지고 **시도했다는 사실만** 보인다. */
    val pending: Int,
    val solvedAt: Instant?,
)

data class ScoreboardParticipant(
    val userId: Long,
    val nickname: String,
    val registeredAt: Instant,
)
