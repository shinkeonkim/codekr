package codekr.api.activity.repository

import codekr.api.activity.ActivityPolicy
import codekr.api.activity.dto.DailyActivity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 일별 활동 집계.
 *
 * 집계 쿼리를 JPA 로 표현하면 오히려 읽기 어렵고, 시간대 변환은 DB 가 훨씬 잘한다.
 * 그래서 이 조회만 SQL 을 직접 쓴다.
 */
@Repository
class ActivityRepository(private val jdbcClient: JdbcClient) {

    fun findDailyCounts(userId: Long, from: LocalDate, to: LocalDate): List<DailyActivity> =
        jdbcClient.sql(
            """
            SELECT (created_at AT TIME ZONE :zone)::date AS day, count(*) AS count
            FROM submissions
            WHERE user_id = :userId
              AND deleted_at IS NULL
              AND kind = 'USER'
              AND status = 'COMPLETED'
              AND (created_at AT TIME ZONE :zone)::date BETWEEN :from AND :to
            GROUP BY day
            ORDER BY day
            """,
        )
            .param("userId", userId)
            .param("zone", ActivityPolicy.ZONE.id)
            .param("from", from)
            .param("to", to)
            .query { rs, _ -> DailyActivity(rs.getDate("day").toLocalDate(), rs.getInt("count")) }
            .list()
}
