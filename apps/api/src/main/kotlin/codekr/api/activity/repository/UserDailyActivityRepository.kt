package codekr.api.activity.repository

import codekr.api.activity.ActivityPolicy
import codekr.api.activity.dto.DailyActivity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 일별 활동 집계 표 (#105).
 *
 * **갱신은 언제나 다시 세어 덮어쓰는 방식이다.** 증분(+1)으로 하면 재채점(#107)이나
 * 제출 삭제 때 값이 어긋나고, 어긋난 것을 되돌리는 장치를 또 만들어야 한다.
 * 하루치를 다시 세는 것은 부분 인덱스가 있어 싸다.
 */
@Repository
class UserDailyActivityRepository(private val jdbcClient: JdbcClient) {

    /** 하루치를 제출에서 다시 세어 덮어쓴다. 0 이 되면 행을 지운다. */
    fun refreshDay(userId: Long, date: LocalDate) {
        val count = countFromSubmissions(userId, date)
        if (count == 0) {
            jdbcClient.sql("DELETE FROM user_daily_activity WHERE user_id = :userId AND activity_date = :date")
                .param("userId", userId)
                .param("date", date)
                .update()
            return
        }
        jdbcClient.sql(
            """
            INSERT INTO user_daily_activity (user_id, activity_date, submission_count)
            VALUES (:userId, :date, :count)
            ON CONFLICT (user_id, activity_date)
            DO UPDATE SET submission_count = EXCLUDED.submission_count, updated_at = now()
            """,
        )
            .param("userId", userId)
            .param("date", date)
            .param("count", count)
            .update()
    }

    /**
     * 그 사용자의 전체를 제출에서 다시 만든다.
     *
     * **집계를 저장하기로 한 이상 이 경로가 반드시 있어야 한다** (#105).
     * 값이 어긋났을 때 되돌릴 방법이 없으면 저장하면 안 되는 것이다.
     */
    fun recomputeAll(userId: Long): Int {
        jdbcClient.sql("DELETE FROM user_daily_activity WHERE user_id = :userId")
            .param("userId", userId)
            .update()

        return jdbcClient.sql(
            """
            INSERT INTO user_daily_activity (user_id, activity_date, submission_count)
            SELECT user_id, (created_at AT TIME ZONE :zone)::date, count(*)
            FROM submissions
            WHERE user_id = :userId AND deleted_at IS NULL AND kind = 'USER' AND status = 'COMPLETED'
            -- 같은 식을 두 번 쓰면 바인딩 자리마다 다른 식으로 취급된다. 순서로 묶는다.
            GROUP BY 1, 2
            """,
        )
            .param("userId", userId)
            .param("zone", ActivityPolicy.ZONE.id)
            .update()
    }

    fun findDailyCounts(userId: Long, from: LocalDate, to: LocalDate): List<DailyActivity> =
        jdbcClient.sql(
            """
            SELECT activity_date, submission_count
            FROM user_daily_activity
            WHERE user_id = :userId AND activity_date BETWEEN :from AND :to
            ORDER BY activity_date
            """,
        )
            .param("userId", userId)
            .param("from", from)
            .param("to", to)
            .query { rs, _ -> DailyActivity(rs.getDate("activity_date").toLocalDate(), rs.getInt("submission_count")) }
            .list()

    /** 활동이 있었던 모든 날짜. 스트릭은 조회 범위가 아니라 전체 기간을 본다 (#81). */
    fun findActiveDates(userId: Long): Set<LocalDate> =
        jdbcClient.sql("SELECT activity_date FROM user_daily_activity WHERE user_id = :userId")
            .param("userId", userId)
            .query { rs, _ -> rs.getDate("activity_date").toLocalDate() }
            .list()
            .toSet()

    private fun countFromSubmissions(userId: Long, date: LocalDate): Int =
        jdbcClient.sql(
            """
            SELECT count(*)
            FROM submissions
            WHERE user_id = :userId
              AND deleted_at IS NULL
              AND kind = 'USER'
              AND status = 'COMPLETED'
              AND (created_at AT TIME ZONE :zone)::date = :date
            """,
        )
            .param("userId", userId)
            .param("zone", ActivityPolicy.ZONE.id)
            .param("date", date)
            .query(Int::class.java)
            .single()
}
