package codekr.api.submission

import codekr.api.activity.ActivityPolicy
import codekr.api.auth.security.JwtTokenProvider
import codekr.api.submission.view.SubmissionViewNotifier
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

/**
 * 하루의 경계에서 코드 열람 알림 (#241).
 *
 * **시계를 얼려서 가장 위험한 순간에 고정한다.** 서울 자정 30분 = UTC 로는 전날 오후
 * 3시 30분이라, 두 시간대의 날짜가 하루 다르다. 이 구간에서만 나던 어긋남을 시험이
 * 언제 돌든 똑같이 재현한다.
 *
 * 시계만 얼리고 DB 는 그대로 둔다 — 앱의 "어제" 와 실제로 저장된 날짜가 맞는지가
 * 확인할 것이고, 그것이 어긋났던 것이 문제였다.
 */
@Import(SubmissionViewDayBoundaryIntegrationTest.FrozenClock::class)
class SubmissionViewDayBoundaryIntegrationTest : IntegrationTestBase() {

    @TestConfiguration
    class FrozenClock {
        @Bean
        @Primary
        fun frozenClock(): Clock = Clock.fixed(SEOUL_MIDNIGHT_ISH, ActivityPolicy.ZONE)
    }

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var notifier: SubmissionViewNotifier
    @Autowired private lateinit var clock: Clock

    @Test
    fun `서울 자정 직후에도 어제 열람을 찾아낸다`() {
        val author = userRepository.save(User("author@codekr.dev", "x", "글쓴이", setOf(UserRole.USER)))
        val viewer = userRepository.save(User("viewer@codekr.dev", "x", "구경꾼", setOf(UserRole.USER)))
        val authorToken = tokenProvider.issueAccessToken(author)
        insertProblem()
        val submissionId = insertSubmission(author.id)
        // 서울 기준 어제(1/15)에 봤다. UTC 로 오늘을 세면 1/14 가 되어 하나도 찾지 못한다.
        insertView(submissionId, viewer.id, LocalDate.now(clock).minusDays(1))

        notifier.notifyYesterday()

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer $authorToken"))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("어제 1명이 내 코드를 봤습니다"))
    }

    @Test
    fun `얼린 시계의 오늘은 서울 날짜다`() {
        // 이 시험이 무엇을 고정하고 있는지 자체를 못 박는다. 시계가 풀리면 여기서 먼저 깨진다.
        assertToday(LocalDate.of(2026, 1, 16))
    }

    private fun assertToday(expected: LocalDate) {
        check(LocalDate.now(clock) == expected) { "얼린 시계의 오늘이 ${LocalDate.now(clock)} 입니다" }
    }

    private fun insertProblem() {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (1, 'two-sum', '두 수의 합', 'ALGORITHM', 1, '설명', true)
            """,
        ).update()
    }

    private fun insertSubmission(userId: Long): Long = jdbcClient.sql(
        """
        INSERT INTO submissions (user_id, problem_id, runtime_id, source_code, status, verdict, visibility, kind)
        VALUES (:userId, 1, 'python:3.12', 'print(1)', 'COMPLETED', 'ACCEPTED', 'PUBLIC', 'USER')
        RETURNING id
        """,
    ).param("userId", userId).query(Long::class.java).single()

    private fun insertView(submissionId: Long, viewerId: Long, day: LocalDate) {
        jdbcClient.sql(
            """
            INSERT INTO submission_views (submission_id, viewer_id, viewed_on)
            VALUES (:submissionId, :viewerId, :day)
            """,
        ).param("submissionId", submissionId).param("viewerId", viewerId).param("day", day).update()
    }

    private companion object {
        /**
         * 서울 2026-01-16 00:30 = UTC 2026-01-15 15:30.
         *
         * 두 가지를 동시에 만족하는 순간이다.
         *   1. **두 시간대의 날짜가 다르다** — 이 어긋남이 문제의 전부였다
         *   2. **오늘이 아니다** — 시계를 주입받지 않고 `LocalDate.now()` 로 되돌리면
         *      실제 오늘을 보게 되어 이 시험이 **반드시** 깨진다
         *
         * 2 가 없으면 시험이 오늘 날짜에 기대게 된다. 실제로 오늘 날짜로 얼렸을 때는,
         * 시간대를 잃은 구현이 UTC 오전 시간대에 그대로 통과했다.
         */
        val SEOUL_MIDNIGHT_ISH: Instant = Instant.parse("2026-01-15T15:30:00Z")
    }
}
