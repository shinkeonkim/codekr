package codekr.api.admin.stats

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 어드민 대시보드가 읽는 집계 (#550).
 *
 * **집계표를 만들지 않고 그때그때 센다.** `ProblemStats`(#84)가 같은 판단을 했다 —
 * 저장하면 재채점(#63)·삭제 때 어긋나고, 어긋난 것을 되돌리는 장치를 또 만들어야 한다.
 *
 * **다만 `submissions` 는 가장 빨리 커지는 표다** (#105 가 활동 집계를 떼어낸 이유).
 * 그래서 여기 있는 질의는 전부 **기간으로 먼저 자른다** — 전체를 훑는 질의를 두지
 * 않는다. 이것이 느려지는 날이 일별 집계표를 만들 때다.
 *
 * 판정·언어 분포는 **최근 것만 본다.** 오래된 것까지 합치면 "지금 무엇이 잘못됐나" 가
 * 옛 자료에 묻힌다 — 대시보드는 추세를 보는 자리지 통계 보고서가 아니다.
 */
@Repository
class AdminStatsRepository(private val jdbcClient: JdbcClient) {

    /** 날짜별 제출 수와 그중 정답 수. 제출이 없는 날은 **행이 없다** (부르는 쪽이 채운다). */
    fun submissionsByDay(from: LocalDate): List<DayCount> =
        jdbcClient.sql(
            """
            SELECT (created_at AT TIME ZONE 'Asia/Seoul')::date       AS day,
                   count(*)                                           AS total,
                   count(*) FILTER (WHERE verdict = 'ACCEPTED')       AS accepted
            FROM submissions
            WHERE deleted_at IS NULL
              AND kind = 'USER'
              AND created_at >= :from
            GROUP BY day
            ORDER BY day
            """,
        )
            .param("from", from.atStartOfDay())
            .query { rs, _ ->
                DayCount(rs.getDate("day").toLocalDate(), rs.getInt("total"), rs.getInt("accepted"))
            }
            .list()

    /** 날짜별 가입 수. 탈퇴한 사람도 **가입한 날에는 들어왔다** — 세는 대상에서 빼지 않는다. */
    fun signupsByDay(from: LocalDate): List<DayCount> =
        jdbcClient.sql(
            """
            SELECT (created_at AT TIME ZONE 'Asia/Seoul')::date AS day, count(*) AS total
            FROM users
            WHERE created_at >= :from
            GROUP BY day
            ORDER BY day
            """,
        )
            .param("from", from.atStartOfDay())
            .query { rs, _ -> DayCount(rs.getDate("day").toLocalDate(), rs.getInt("total"), 0) }
            .list()

    /**
     * 판정 분포. **`SYSTEM_ERROR` 를 보려고 있는 값이다** — 그것이 늘면 우리 잘못이고,
     * 사용자는 자기 코드를 의심하며 시간을 쓴다.
     */
    fun verdictShare(from: LocalDate): List<NamedCount> =
        jdbcClient.sql(
            """
            SELECT coalesce(verdict, '(채점 중)') AS name, count(*) AS total
            FROM submissions
            WHERE deleted_at IS NULL
              AND kind = 'USER'
              AND created_at >= :from
            GROUP BY name
            ORDER BY total DESC
            """,
        )
            .param("from", from.atStartOfDay())
            .query { rs, _ -> NamedCount(rs.getString("name"), rs.getInt("total")) }
            .list()

    /** 언어별 제출 비중. 런타임을 늘릴지 줄일지의 근거다 (#97·#419). */
    fun runtimeShare(from: LocalDate): List<NamedCount> =
        jdbcClient.sql(
            """
            SELECT runtime_id AS name, count(*) AS total
            FROM submissions
            WHERE deleted_at IS NULL
              AND kind = 'USER'
              AND created_at >= :from
            GROUP BY runtime_id
            ORDER BY total DESC
            """,
        )
            .param("from", from.atStartOfDay())
            .query { rs, _ -> NamedCount(rs.getString("name"), rs.getInt("total")) }
            .list()

    /**
     * 유형별 **공개된** 문제 수.
     *
     * 기간으로 자르지 않는다 — 이것은 추세가 아니라 **지금 우리가 가진 것**이다.
     * `problems` 는 사람 수·제출 수만큼 커지지 않는다.
     */
    fun problemsByKind(): List<NamedCount> =
        jdbcClient.sql(
            """
            SELECT problem_kind AS name, count(*) AS total
            FROM problems
            WHERE deleted_at IS NULL AND published = true
            GROUP BY problem_kind
            ORDER BY total DESC
            """,
        )
            .query { rs, _ -> NamedCount(rs.getString("name"), rs.getInt("total")) }
            .list()
}

/** 하루치 숫자. [accepted] 는 제출에만 뜻이 있다. */
data class DayCount(val day: LocalDate, val total: Int, val accepted: Int)

/** 이름표가 붙은 숫자 (판정·언어·유형). */
data class NamedCount(val name: String, val total: Int)
