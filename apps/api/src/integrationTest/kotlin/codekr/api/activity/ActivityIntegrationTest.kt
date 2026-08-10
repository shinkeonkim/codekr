package codekr.api.activity

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.ZoneId

class ActivityIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private val zone = ZoneId.of("Asia/Seoul")
    private var userId: Long = 0
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER)))
        userId = user.id
        token = tokenProvider.issueAccessToken(user)
        // 문제 하나를 만들어 제출이 참조할 수 있게 한다.
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (1, 'two-sum', '두 수의 합', 'ALGORITHM', 1, '설명', true)
            """,
        ).update()
    }

    @Test
    fun `채점이 끝난 제출만 활동으로 센다`() {
        submissionOn(LocalDate.now(zone), status = "COMPLETED")
        submissionOn(LocalDate.now(zone), status = "PENDING")

        mockMvc.perform(get("/api/v1/users/me/activity").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.activeDayCount").value(1))
    }

    @Test
    fun `같은 날 여러 번 제출하면 강도는 올라가고 스트릭은 하루로 센다`() {
        val today = LocalDate.now(zone)
        repeat(3) { submissionOn(today) }

        mockMvc.perform(get("/api/v1/users/me/activity").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.days.length()").value(1))
            .andExpect(jsonPath("$.days[0].count").value(3))
            .andExpect(jsonPath("$.totalCount").value(3))
            .andExpect(jsonPath("$.currentStreak").value(1))
    }

    @Test
    fun `연속 활동과 공백이 스트릭에 반영된다`() {
        val today = LocalDate.now(zone)
        listOf(0L, 1L, 2L).forEach { submissionOn(today.minusDays(it)) }
        // 공백을 두고 더 긴 과거 구간
        listOf(10L, 11L, 12L, 13L).forEach { submissionOn(today.minusDays(it)) }

        mockMvc.perform(get("/api/v1/users/me/activity").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentStreak").value(3))
            .andExpect(jsonPath("$.longestStreak").value(4))
    }

    @Test
    fun `활동이 없으면 빈 상태를 돌려준다`() {
        mockMvc.perform(get("/api/v1/users/me/activity").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.days.length()").value(0))
            .andExpect(jsonPath("$.currentStreak").value(0))
            .andExpect(jsonPath("$.longestStreak").value(0))
            .andExpect(jsonPath("$.timeZone").value("Asia/Seoul"))
    }

    @Test
    fun `자정 직전 제출은 그날의 활동이다`() {
        val today = LocalDate.now(zone)
        // 한국 시간 23시 50분 — UTC 로 자르면 전날로 잡히는 시각이다.
        insertSubmission(today.atTime(23, 50).atZone(zone).toInstant().toString(), "COMPLETED")

        mockMvc.perform(get("/api/v1/users/me/activity").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.days[0].date").value(today.toString()))
    }

    @Test
    fun `조회 기간을 벗어난 활동은 빠진다`() {
        val today = LocalDate.now(zone)
        submissionOn(today.minusDays(400))
        submissionOn(today)

        mockMvc.perform(get("/api/v1/users/me/activity").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.days.length()").value(1))
    }

    @Test
    fun `시작일이 종료일보다 늦으면 400 이다`() {
        mockMvc.perform(
            get("/api/v1/users/me/activity?from=2026-08-10&to=2026-08-01")
                .header("Authorization", "Bearer $token"),
        ).andExpect(status().isBadRequest)
    }

    // ── 연도별 보기 (#81) ──────────────────────────────────────────────────────

    @Test
    fun `연도를 지정하면 그 해만 그래프에 담긴다`() {
        val thisYear = LocalDate.now(zone).year
        submissionOn(LocalDate.of(thisYear - 1, 6, 1))
        submissionOn(LocalDate.of(thisYear - 1, 6, 2))
        submissionOn(LocalDate.now(zone))

        mockMvc.perform(
            get("/api/v1/users/me/activity?year=${thisYear - 1}").header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.days.length()").value(2))
            .andExpect(jsonPath("$.from").value("${thisYear - 1}-01-01"))
            .andExpect(jsonPath("$.to").value("${thisYear - 1}-12-31"))
    }

    @Test
    fun `최장 스트릭은 조회한 연도가 아니라 전체 기간 기준이다`() {
        val thisYear = LocalDate.now(zone).year
        // 작년에 5일 연속.
        repeat(5) { submissionOn(LocalDate.of(thisYear - 1, 6, 1).plusDays(it.toLong())) }
        // 올해는 하루만.
        submissionOn(LocalDate.now(zone))

        // 올해만 조회해도 최장 기록은 작년 것이 나와야 한다.
        mockMvc.perform(get("/api/v1/users/me/activity?year=$thisYear").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.days.length()").value(1))
            .andExpect(jsonPath("$.longestStreak").value(5))
    }

    @Test
    fun `연말과 연초에 걸친 연속은 끊기지 않는다`() {
        val thisYear = LocalDate.now(zone).year
        // 12/30, 12/31, 1/1, 1/2 — 연도 경계를 넘는 4일 연속.
        submissionOn(LocalDate.of(thisYear - 1, 12, 30))
        submissionOn(LocalDate.of(thisYear - 1, 12, 31))
        submissionOn(LocalDate.of(thisYear, 1, 1))
        submissionOn(LocalDate.of(thisYear, 1, 2))

        mockMvc.perform(get("/api/v1/users/me/activity?year=$thisYear").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.longestStreak").value(4))
    }

    @Test
    fun `선택할 수 있는 연도는 활동이 있는 해와 올해다`() {
        val thisYear = LocalDate.now(zone).year
        submissionOn(LocalDate.of(thisYear - 2, 3, 1))

        mockMvc.perform(get("/api/v1/users/me/activity").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            // 최신 연도가 앞. 활동이 없는 해(작년)는 선택지에 없다.
            .andExpect(jsonPath("$.availableYears[0]").value(thisYear))
            .andExpect(jsonPath("$.availableYears[1]").value(thisYear - 2))
            .andExpect(jsonPath("$.availableYears.length()").value(2))
    }

    @Test
    fun `서비스 시작 이전 연도는 400 이다`() {
        mockMvc.perform(get("/api/v1/users/me/activity?year=1999").header("Authorization", "Bearer $token"))
            .andExpect(status().isBadRequest)
    }

    private fun submissionOn(date: LocalDate, status: String = "COMPLETED") {
        insertSubmission(date.atTime(12, 0).atZone(zone).toInstant().toString(), status)
    }

    private fun insertSubmission(createdAt: String, status: String) {
        jdbcClient.sql(
            """
            INSERT INTO submissions
                (user_id, problem_id, runtime_id, source_code, status, kind, created_at, updated_at)
            VALUES (:userId, 1, 'python:3.12', 'print(3)', :status, 'USER', :createdAt::timestamptz, now())
            """,
        )
            .param("userId", userId)
            .param("status", status)
            .param("createdAt", createdAt)
            .update()
    }
}
