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

    /**
     * 활동이 있었던 **모든** 날짜.
     *
     * 스트릭은 조회 범위가 아니라 전체 기간을 봐야 한다 (#81). 2026년만 조회했다고
     * 최장 스트릭이 2026년 안으로 잘리면 안 되고, 12/31~1/1 로 이어지는 연속도
     * 끊겨 보이면 안 된다.
     *
     * 활동한 날마다 한 행이므로 몇 년을 모아도 수천 건이다.
     */
    fun findActiveDates(userId: Long): Set<LocalDate> =
        jdbcClient.sql(
            """
            SELECT DISTINCT (created_at AT TIME ZONE :zone)::date AS day
            FROM submissions
            WHERE user_id = :userId
              AND deleted_at IS NULL
              AND kind = 'USER'
              AND status = 'COMPLETED'
            """,
        )
            .param("userId", userId)
            .param("zone", ActivityPolicy.ZONE.id)
            .query { rs, _ -> rs.getDate("day").toLocalDate() }
            .list()
            .toSet()
}
