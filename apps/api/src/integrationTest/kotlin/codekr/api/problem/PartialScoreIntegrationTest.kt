package codekr.api.problem

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.queue.message.JudgeEventMessage
import codekr.api.submission.entity.Verdict
import codekr.api.submission.service.JudgeResultRecorder
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Range
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 부분 점수 (#473).
 *
 * **99개를 맞혀도 지금은 그냥 틀린 답이었다.** 배우는 사람은 어디까지 왔는지 모른 채
 * 다시 낸다.
 *
 * **랭킹에는 반영하지 않는다** — 만점만 "풀었다" 로 본다. 그 결정이 이 시험에 있다.
 */
class PartialScoreIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var redisTemplate: StringRedisTemplate
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var recorder: JudgeResultRecorder

    private lateinit var adminToken: String
    private lateinit var userToken: String
    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        val admin = userRepository.save(
            User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.SUPERUSER)),
        )
        adminToken = tokenProvider.issueAccessToken(admin)
        val user = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER)))
        userId = user.id
        userToken = tokenProvider.issueAccessToken(user)
    }

    @Test
    fun `묶음별 점수를 가진 문제를 낼 수 있다`() {
        val id = create()

        mockMvc.perform(get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.testcases[0].groupNo").value(1))
    }

    @Test
    fun `묶음과 점수가 채점 작업에 실린다`() {
        // **채점기는 DB 를 읽지 않는다** (ADR-0004) — 묶음도 실려 가야 한다.
        create()

        mockMvc.perform(
            post("/api/v1/problems/subtasks/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.13","sourceCode":"print(1)"}"""),
        ).andExpect(status().isAccepted)

        val queued = redisTemplate.opsForStream<String, String>()
            .range("codekr:judge:normal", Range.unbounded())
            .orEmpty()
            .joinToString(" ") { it.value.values.joinToString(" ") }

        assert(queued.contains("groups")) { "묶음이 작업에 없다: $queued" }
        assert(queued.contains("\"score\":40")) { "묶음 점수가 작업에 없다: $queued" }
    }

    @Test
    fun `부분 점수가 제출에 남는다`() {
        val submissionId = submit()

        recorder.record(completed(verdict = Verdict.WRONG_ANSWER, submissionId = submissionId, score = 40))

        mockMvc.perform(
            get("/api/v1/submissions/$submissionId").header("Authorization", "Bearer $userToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.score").value(40))
            .andExpect(jsonPath("$.maxScore").value(100))
    }

    @Test
    fun `부분 점수는 랭킹에 반영되지 않는다`() {
        /*
          **만점만 "풀었다" 로 본다** (#57). 부분 점수의 교육적 값은 화면에 보이는 것으로
          대부분 얻어지고, 랭킹까지 열면 #57·#58·#84·#105 가 전부 흔들린다.
        */
        val submissionId = submit()

        recorder.record(completed(verdict = Verdict.WRONG_ANSWER, submissionId = submissionId, score = 40))

        val scored = jdbcClient.sql("SELECT count(*) FROM user_problem_scores WHERE user_id = :u")
            .param("u", userId).query(Int::class.java).single()
        assert(scored == 0) { "부분 점수로는 랭킹 점수가 생기면 안 됩니다: ${scored}행" }
    }

    @Test
    fun `묶음이 없는 문제의 제출은 지금과 같다`() {
        // 점수는 null 이다 — **0 점과 다르다.** 0 으로 두면 만점짜리 문제가 0점으로 보인다.
        val id = create(slug = "plain", groups = false)
        val submissionId = jdbcClient.sql(
            """
            INSERT INTO submissions (user_id, problem_id, runtime_id, source_code, status, kind)
            VALUES (:u, :p, 'python:3.13', 'print(1)', 'PENDING', 'USER') RETURNING id
            """,
        ).param("u", userId).param("p", id).query(Long::class.java).single()

        recorder.record(completed(verdict = Verdict.ACCEPTED, submissionId = submissionId, score = 0))

        val score = jdbcClient.sql("SELECT score FROM submissions WHERE id = :id")
            .param("id", submissionId).query(Int::class.java).optional().orElse(null)
        assert(score == null) { "묶음이 없으면 점수는 null 이어야 합니다: $score" }
    }

    private fun completed(verdict: Verdict, submissionId: Long, score: Int) = JudgeEventMessage(
        type = JudgeEventMessage.TYPE_COMPLETED,
        submissionId = submissionId,
        verdict = verdict.name,
        passedCount = if (verdict == Verdict.ACCEPTED) 4 else 2,
        totalCount = 4,
        score = score,
        maxScore = if (score > 0) 100 else 0,
    )

    private fun submit(): Long {
        create()
        val response = mockMvc.perform(
            post("/api/v1/problems/subtasks/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.13","sourceCode":"print(1)"}"""),
        ).andExpect(status().isAccepted).andReturn().response.contentAsString
        return Regex("\"submissionId\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun create(slug: String = "subtasks", groups: Boolean = true): Long {
        val groupJson = if (!groups) "" else """
            "testcaseGroups": [
              {"groupNo": 1, "score": 40, "label": "N ≤ 1,000"},
              {"groupNo": 2, "score": 60, "label": "만점"}
            ],
        """.trimIndent()
        val groupNo = if (groups) "\"groupNo\": 1," else ""
        val response = mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "slug": "$slug", "title": "부분 점수 문제",
                      "category": "ALGORITHM", "difficulty": "SILVER_5",
                      "description": "설명", "published": true,
                      $groupJson
                      "testcases": [
                        {"seq": 1, $groupNo "input": "1", "expectedOutput": "1\n", "visibility": "PUBLIC"}
                      ],
                      "templates": []
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }
}
