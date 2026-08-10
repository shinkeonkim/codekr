package codekr.api.submission.view

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 제출 코드 열람 기록 (#136).
 *
 * **하루 한 행이다.** 같은 사람이 같은 날 몇 번을 봐도 한 번으로 센다 —
 * 새로고침이나 뒤로가기가 숫자를 부풀리지 않고, 쓰기도 폭주하지 않는다.
 */
@Repository
class SubmissionViewRepository(private val jdbcClient: JdbcClient) {

    fun record(submissionId: Long, viewerId: Long, day: LocalDate) {
        jdbcClient.sql(
            """
            INSERT INTO submission_views (submission_id, viewer_id, viewed_on)
            VALUES (:submissionId, :viewerId, :day)
            ON CONFLICT (submission_id, viewer_id, viewed_on) DO NOTHING
            """,
        )
            .param("submissionId", submissionId)
            .param("viewerId", viewerId)
            .param("day", day)
            .update()
    }

    /**
     * 그날 작성자별로 몇 명이 봤는지.
     *
     * **사람 수를 센다.** 누가 봤는지는 세지도, 알리지도 않는다 — 닉네임을 알리면
     * 조회자 쪽 프라이버시를 그만큼 가져간다. 수만 알려도 "읽히고 있다" 는 목적은 이룬다.
     */
    fun dailyViewerCounts(day: LocalDate): List<AuthorViewCount> =
        jdbcClient.sql(
            """
            SELECT s.user_id, count(DISTINCT v.viewer_id)::int AS viewers, count(DISTINCT s.id)::int AS submissions
            FROM submission_views v
            JOIN submissions s ON s.id = v.submission_id
            WHERE v.viewed_on = :day AND s.deleted_at IS NULL
            GROUP BY s.user_id
            """,
        )
            .param("day", day)
            .query { rs, _ ->
                AuthorViewCount(rs.getLong("user_id"), rs.getInt("viewers"), rs.getInt("submissions"))
            }
            .list()

    /** 보관 기간이 지난 기록을 지운다 (ADR-0007). */
    fun deleteOlderThan(day: LocalDate): Int =
        jdbcClient.sql("DELETE FROM submission_views WHERE viewed_on < :day")
            .param("day", day)
            .update()
}

data class AuthorViewCount(val authorId: Long, val viewerCount: Int, val submissionCount: Int)
