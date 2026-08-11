package codekr.api.contest

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
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 대회 순위표 (#63). */
class ScoreboardIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private lateinit var managerToken: String
    private lateinit var userToken: String
    private var fastId: Long = 0
    private var slowId: Long = 0

    /** 대회는 2시간 전에 시작해 10분 뒤 끝난다 — 동결(30분 전)이 이미 지난 상태다. */
    private val startsAt: Instant = Instant.now().minus(110, ChronoUnit.MINUTES)
    private val endsAt: Instant = startsAt.plus(120, ChronoUnit.MINUTES)
    private val freezeAt: Instant = endsAt.minus(30, ChronoUnit.MINUTES)

    @BeforeEach
    fun setUp() {
        val manager = userRepository.save(
            User("m@codekr.dev", "x", "운영자", setOf(UserRole.USER, UserRole.CONTEST_MANAGER)),
        )
        managerToken = tokenProvider.issueAccessToken(manager)
        val fast = userRepository.save(User("fast@codekr.dev", "x", "빠른사람", setOf(UserRole.USER)))
        fastId = fast.id
        userToken = tokenProvider.issueAccessToken(fast)
        slowId = userRepository.save(User("slow@codekr.dev", "x", "느린사람", setOf(UserRole.USER))).id

        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (1, 'a', 'A 문제', 'ALGORITHM', 1, '설명', true),
                   (2, 'b', 'B 문제', 'ALGORITHM', 5, '설명', true)
            """,
        ).update()

        jdbcClient.sql(
            """
            INSERT INTO contests (id, slug, title, starts_at, ends_at, freeze_minutes, status)
            VALUES (1, 'cup', '컵', :startsAt::timestamptz, :endsAt::timestamptz, 30, 'PUBLISHED')
            """,
        ).param("startsAt", startsAt.toString()).param("endsAt", endsAt.toString()).update()

        jdbcClient.sql(
            """
            INSERT INTO contest_problems (contest_id, problem_id, seq, score)
            VALUES (1, 1, 1, 100), (1, 2, 2, 200)
            """,
        ).update()

        jdbcClient.sql(
            """
            INSERT INTO contest_registrations (contest_id, user_id, registered_at)
            VALUES (1, :fast, :startsAt::timestamptz), (1, :slow, :startsAt::timestamptz)
            """,
        ).param("fast", fastId).param("slow", slowId).param("startsAt", startsAt.toString()).update()
    }

    @Test
    fun `총점이 같으면 먼저 푼 사람이 앞선다`() {
        solve(fastId, problemId = 1, minutesIn = 10)
        solve(slowId, problemId = 1, minutesIn = 40)

        mockMvc.perform(get("/api/v1/contests/cup/scoreboard"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rows[0].nickname").value("빠른사람"))
            .andExpect(jsonPath("$.rows[0].rank").value(1))
            .andExpect(jsonPath("$.rows[0].totalScore").value(100))
            .andExpect(jsonPath("$.rows[1].nickname").value("느린사람"))
            // 맞힌 시각은 대회 시작 후 몇 분인지로 보여준다.
            .andExpect(jsonPath("$.rows[0].cells[0].solvedMinutes").value(10))
    }

    @Test
    fun `배점이 큰 문제를 푼 사람이 앞선다`() {
        solve(fastId, problemId = 1, minutesIn = 5)
        solve(slowId, problemId = 2, minutesIn = 80)

        mockMvc.perform(get("/api/v1/contests/cup/scoreboard"))
            .andExpect(jsonPath("$.rows[0].nickname").value("느린사람"))
            .andExpect(jsonPath("$.rows[0].totalScore").value(200))
    }

    @Test
    fun `동결 뒤의 결과는 참가자에게 감춰지고 시도 사실은 보인다`() {
        // #63 의 완료 조건이다.
        solve(fastId, problemId = 1, minutesIn = 10)
        // 동결 이후에 B 를 맞혔다.
        solve(fastId, problemId = 2, minutesIn = 100)

        mockMvc.perform(get("/api/v1/contests/cup/scoreboard"))
            .andExpect(jsonPath("$.frozen").value(true))
            // 점수는 동결 시점 그대로다.
            .andExpect(jsonPath("$.rows[0].totalScore").value(100))
            .andExpect(jsonPath("$.rows[0].cells[1].solved").value(false))
            // **시도했다는 사실은 감추지 않는다** — 감추면 순위표가 후반에 무의미해진다 (#86).
            .andExpect(jsonPath("$.rows[0].cells[1].pending").value(1))
    }

    @Test
    fun `어드민은 동결 중에도 실제 순위를 본다`() {
        solve(fastId, problemId = 1, minutesIn = 10)
        solve(fastId, problemId = 2, minutesIn = 100)

        mockMvc.perform(
            get("/api/v1/contests/cup/scoreboard")
                .param("actual", "true")
                .header("Authorization", "Bearer $managerToken"),
        )
            .andExpect(jsonPath("$.rows[0].totalScore").value(300))
            .andExpect(jsonPath("$.rows[0].cells[1].solved").value(true))
    }

    @Test
    fun `권한이 없으면 실제 순위를 달라고 해도 참가자와 같은 것을 본다`() {
        solve(fastId, problemId = 1, minutesIn = 10)
        solve(fastId, problemId = 2, minutesIn = 100)

        mockMvc.perform(
            get("/api/v1/contests/cup/scoreboard")
                .param("actual", "true")
                .header("Authorization", "Bearer $userToken"),
        ).andExpect(jsonPath("$.rows[0].totalScore").value(100))
    }

    @Test
    fun `제외된 문제는 점수를 주지 않지만 시도 기록은 남는다`() {
        solve(fastId, problemId = 1, minutesIn = 10)
        jdbcClient.sql("UPDATE contest_problems SET excluded_at = now() WHERE contest_id = 1 AND problem_id = 1")
            .update()

        mockMvc.perform(get("/api/v1/contests/cup/scoreboard").param("actual", "true")
            .header("Authorization", "Bearer $managerToken"))
            .andExpect(jsonPath("$.problems[0].excluded").value(true))
            .andExpect(jsonPath("$.rows[0].totalScore").value(0))
            // 지우지 않는 이유는 그 문제로 낸 제출이 남아 있기 때문이다.
            .andExpect(jsonPath("$.rows[0].cells[0].attempts").value(1))
    }

    @Test
    fun `재채점 중이면 순위표가 그 사실을 알린다`() {
        solve(fastId, problemId = 1, minutesIn = 10)
        jdbcClient.sql(
            "INSERT INTO rejudge_batches (problem_id, reason, requested_by, target_count) VALUES (1, '이유', :by, 1)",
        ).param("by", fastId).update()
        jdbcClient.sql("UPDATE submissions SET rejudge_batch_id = 1 WHERE contest_id = 1").update()

        // 중간 상태의 순위를 보여주면 참가자가 잘못된 정보로 판단한다.
        mockMvc.perform(get("/api/v1/contests/cup/scoreboard").param("actual", "true")
            .header("Authorization", "Bearer $managerToken"))
            .andExpect(jsonPath("$.rejudgeInProgress").value(true))
    }

    @Test
    fun `틀린 제출은 시도 횟수만 늘린다`() {
        submit(fastId, problemId = 1, minutesIn = 5, verdict = "WRONG_ANSWER")
        submit(fastId, problemId = 1, minutesIn = 8, verdict = "WRONG_ANSWER")
        solve(fastId, problemId = 1, minutesIn = 12)

        mockMvc.perform(get("/api/v1/contests/cup/scoreboard").param("actual", "true")
            .header("Authorization", "Bearer $managerToken"))
            .andExpect(jsonPath("$.rows[0].cells[0].attempts").value(3))
            .andExpect(jsonPath("$.rows[0].cells[0].solved").value(true))
            // 오답에 감점이 없다 — 총점은 배점 그대로다.
            .andExpect(jsonPath("$.rows[0].totalScore").value(100))
    }

    @Test
    fun `문제마다 몇 명이 풀었는지 보인다`() {
        solve(fastId, problemId = 1, minutesIn = 10)
        solve(slowId, problemId = 1, minutesIn = 20)

        mockMvc.perform(get("/api/v1/contests/cup/scoreboard"))
            .andExpect(jsonPath("$.problems[0].solvedCount").value(2))
            .andExpect(jsonPath("$.problems[1].solvedCount").value(0))
    }

    @Test
    fun `아무도 풀지 못해도 참가자는 모두 나온다`() {
        mockMvc.perform(get("/api/v1/contests/cup/scoreboard"))
            .andExpect(jsonPath("$.rows.length()").value(2))
            .andExpect(jsonPath("$.rows[0].totalScore").value(0))
    }

    private fun solve(userId: Long, problemId: Long, minutesIn: Long) =
        submit(userId, problemId, minutesIn, "ACCEPTED")

    private fun submit(userId: Long, problemId: Long, minutesIn: Long, verdict: String) {
        val at = startsAt.plus(minutesIn, ChronoUnit.MINUTES)
        check(at.isBefore(freezeAt) || verdict == "ACCEPTED" || true)
        jdbcClient.sql(
            """
            INSERT INTO submissions
                (user_id, problem_id, runtime_id, source_code, status, verdict, kind, contest_id,
                 created_at, updated_at, total_count)
            VALUES (:userId, :problemId, 'python:3.12', 'print(3)', 'COMPLETED', :verdict, 'USER', 1,
                    :at::timestamptz, now(), 1)
            """,
        )
            .param("userId", userId)
            .param("problemId", problemId)
            .param("verdict", verdict)
            .param("at", at.toString())
            .update()
    }
}
