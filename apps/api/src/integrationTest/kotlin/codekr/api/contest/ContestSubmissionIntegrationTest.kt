package codekr.api.contest

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.queue.QueueKeys
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 대회 제출 경로와 별도 채점 큐 (#62). */
class ContestSubmissionIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var redis: StringRedisTemplate

    private lateinit var adminToken: String
    private lateinit var userToken: String
    private var contestId: Long = 0

    @BeforeEach
    fun setUp() {
        QueueKeys.JUDGE_STREAMS.forEach { redis.delete(it) }

        val admin = userRepository.save(
            User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.SUPERUSER)),
        )
        adminToken = tokenProvider.issueAccessToken(admin)
        val user = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER)))
        userToken = tokenProvider.issueAccessToken(user)

        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (1, 'two-sum', '두 수의 합', 'ALGORITHM', 1, '설명', true),
                   (2, 'three-sum', '세 수의 합', 'ALGORITHM', 5, '설명', true)
            """,
        ).update()
        jdbcClient.sql(
            """
            INSERT INTO problem_testcases (problem_id, seq, input, expected_output, visibility)
            VALUES (1, 1, '1 2', '3', 'HIDDEN'), (2, 1, '1 2 3', '6', 'HIDDEN')
            """,
        ).update()

        contestId = createRunningContest()
        register()
    }

    @Test
    fun `대회 제출은 대회 전용 큐로 간다`() {
        // **같은 큐를 쓰면 대회 제출이 평소 사용자의 채점을 몇 분씩 밀어낸다.**
        submitToContest("two-sum").andExpect(status().isAccepted)

        assertEquals(1, queueLength(QueueKeys.JUDGE_STREAM_CONTEST))
        assertEquals(0, queueLength(QueueKeys.JUDGE_STREAM_NORMAL), "평소 큐에 섞이면 안 됩니다")
    }

    @Test
    fun `평소 제출은 대회 큐로 가지 않는다`() {
        mockMvc.perform(
            post("/api/v1/problems/two-sum/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.12","sourceCode":"print(3)"}"""),
        ).andExpect(status().isAccepted)

        assertEquals(1, queueLength(QueueKeys.JUDGE_STREAM_NORMAL))
        assertEquals(0, queueLength(QueueKeys.JUDGE_STREAM_CONTEST))
    }

    @Test
    fun `참가 등록 없이는 대회에 제출할 수 없다`() {
        val outsider = userRepository.save(User("out@codekr.dev", "x", "구경꾼", setOf(UserRole.USER)))
        val outsiderToken = tokenProvider.issueAccessToken(outsider)

        mockMvc.perform(
            post("/api/v1/contests/live/problems/two-sum/submissions")
                .header("Authorization", "Bearer $outsiderToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.12","sourceCode":"print(3)"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `같은 문제를 연속으로 내면 막힌다`() {
        // 한 사람이 초당 여러 번 내면 그만큼 워커가 묶이고, 그 대가는 다른 참가자가 치른다.
        submitToContest("two-sum").andExpect(status().isAccepted)
        // 429 다 — 고칠 것이 있어서 막는 것이 아니라 기다리라는 뜻이다 (#189).
        submitToContest("two-sum").andExpect(status().isTooManyRequests)

        assertEquals(1, queueLength(QueueKeys.JUDGE_STREAM_CONTEST))
    }

    @Test
    fun `다른 문제는 간격 제한에 걸리지 않는다`() {
        // A 문제를 반복 제출하는 것이 B 문제 제출을 막으면 안 된다.
        submitToContest("two-sum").andExpect(status().isAccepted)
        submitToContest("three-sum").andExpect(status().isAccepted)

        assertEquals(2, queueLength(QueueKeys.JUDGE_STREAM_CONTEST))
    }

    @Test
    fun `제외된 문제에는 제출할 수 없다`() {
        mockMvc.perform(
            put("/api/v1/admin/contests/$contestId/problems/1/exclusion")
                .param("excluded", "true")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isOk)

        submitToContest("two-sum").andExpect(status().isBadRequest)
    }

    @Test
    fun `종료된 대회에는 제출할 수 없다`() {
        val endedId = createContest("done", startsIn = Duration.ofHours(-3), length = Duration.ofHours(2))
        publish(endedId)

        mockMvc.perform(
            post("/api/v1/contests/done/problems/two-sum/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.12","sourceCode":"print(3)"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `대회 제출은 어느 대회의 것인지 남는다`() {
        submitToContest("two-sum").andExpect(status().isAccepted)

        val stored = jdbcClient.sql("SELECT contest_id FROM submissions WHERE contest_id IS NOT NULL")
            .query(Long::class.java).list()
        assertEquals(listOf(contestId), stored)
    }

    @Test
    fun `모니터링은 대회 큐도 본다`() {
        // 적체를 볼 수 없으면 워커를 언제 늘려야 하는지 알 수 없다.
        assertTrue(QueueKeys.JUDGE_STREAM_CONTEST in QueueKeys.JUDGE_STREAMS)
        assertTrue(QueueKeys.JUDGE_STREAM_CONTEST !in QueueKeys.JUDGE_PRIORITY_STREAMS)
    }

    @Test
    fun `대회 제출은 감사 이력을 남긴다`() {
        // 부정행위 의심이 생겼을 때 판단할 근거가 없으면 아무 조치도 할 수 없다 (#148).
        submitToContest("two-sum").andExpect(status().isAccepted)

        val rows = jdbcClient.sql("SELECT count(*) FROM contest_submission_audits")
            .query(Int::class.java).single()
        assertEquals(1, rows)
    }

    @Test
    fun `평소 제출은 감사 이력을 남기지 않는다`() {
        // 모든 사용자의 접속 이력이 쌓이는 것은 이 기능이 필요로 하지 않는 정보다.
        mockMvc.perform(
            post("/api/v1/problems/two-sum/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.12","sourceCode":"print(3)"}"""),
        ).andExpect(status().isAccepted)

        val rows = jdbcClient.sql("SELECT count(*) FROM contest_submission_audits")
            .query(Int::class.java).single()
        assertEquals(0, rows)
    }

    @Test
    fun `같은 주소에서 낸 계정이 여럿이면 어드민에게 보인다`() {
        submitToContest("two-sum").andExpect(status().isAccepted)
        // 다른 참가자가 같은 주소에서 낸 것처럼 만든다.
        val outsider = userRepository.save(
            User("second@codekr.dev", "x", "두번째", setOf(UserRole.USER)),
        )
        jdbcClient.sql(
            """
            INSERT INTO contest_submission_audits (submission_id, contest_id, user_id, ip)
            SELECT id, contest_id, :userId, (SELECT ip FROM contest_submission_audits LIMIT 1)
            FROM submissions WHERE contest_id IS NOT NULL LIMIT 1
            ON CONFLICT (submission_id) DO UPDATE SET user_id = :userId
            """,
        ).param("userId", outsider.id).update()

        mockMvc.perform(
            get("/api/v1/admin/contests/$contestId/audit/shared-addresses")
                .header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            // 전체 목록을 내리면 감사가 아니라 감시가 된다. 겹치는 것만 나온다.
            .andExpect(jsonPath("$.length()").value(0))
    }

    private fun submitToContest(problemSlug: String) = mockMvc.perform(
        post("/api/v1/contests/live/problems/$problemSlug/submissions")
            .header("Authorization", "Bearer $userToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"runtimeId":"python:3.12","sourceCode":"print(3)"}"""),
    )

    private fun queueLength(stream: String): Int = redis.opsForStream<String, String>().size(stream).toInt()

    private fun createRunningContest(): Long {
        val id = createContest("live", startsIn = Duration.ofMinutes(-10))
        publish(id)
        return id
    }

    private fun createContest(slug: String, startsIn: Duration, length: Duration = Duration.ofHours(2)): Long {
        val startsAt = Instant.now().plus(startsIn)
        val response = mockMvc.perform(
            post("/api/v1/admin/contests")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "slug": "$slug", "title": "대회", "description": "",
                      "startsAt": "$startsAt", "endsAt": "${startsAt.plus(length)}",
                      "freezeMinutes": 0, "registrationOpenDuring": true,
                      "problems": [{"problemId": 1, "seq": 1, "score": 100},
                                   {"problemId": 2, "seq": 2, "score": 200}]
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun publish(id: Long) {
        mockMvc.perform(
            put("/api/v1/admin/contests/$id/status")
                .param("status", "PUBLISHED")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isOk)
    }

    private fun register() {
        mockMvc.perform(
            post("/api/v1/contests/live/registrations").header("Authorization", "Bearer $userToken"),
        ).andExpect(status().isNoContent)
    }
}
