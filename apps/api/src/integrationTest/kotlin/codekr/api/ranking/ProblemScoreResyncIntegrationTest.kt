package codekr.api.ranking

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.queue.message.JudgeEventMessage
import codekr.api.ranking.repository.UserProblemScoreRepository
import codekr.api.submission.entity.Verdict
import codekr.api.submission.service.JudgeResultRecorder
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals

/**
 * 문제가 바뀌면 이미 맞힌 사람의 점수도 따라간다 (#194).
 *
 * **어드민 API 를 그대로 통과시킨다.** 재계산을 부르는 것이 저장 경로라는 것 자체가
 * 이 이슈의 내용이므로, 서비스를 직접 부르면 정작 빠져 있던 연결을 시험하지 못한다.
 */
class ProblemScoreResyncIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var recorder: JudgeResultRecorder
    @Autowired private lateinit var scoreRepository: UserProblemScoreRepository

    private lateinit var adminToken: String
    private var solverId: Long = 0
    private var problemId: Long = 0

    /** BRONZE_5 = 레벨 1 = 10점, GOLD_5 = 레벨 11 = 93점. */
    private val bronzeScore = 10
    private val goldScore = 93

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.ADMIN))),
        )
        solverId = userRepository.save(User("solver@codekr.dev", "x", "먼저푼사람", setOf(UserRole.USER))).id

        problemId = createProblem("BRONZE_5")
        accept(solverId, problemId)
        assertEquals(bronzeScore to 1, scoreRepository.totalsOf(solverId))
    }

    @Test
    fun `난이도를 올리면 먼저 푼 사람의 점수도 오른다`() {
        // 이것이 이슈의 본체다. 전에는 난이도를 올린 뒤에 푼 사람만 오른 점수를 받아,
        // 같은 문제를 푼 두 사람의 점수가 **푼 시점 때문에** 갈렸다.
        save(problemId, difficulty = "GOLD_5")

        assertEquals(goldScore to 1, scoreRepository.totalsOf(solverId))
    }

    @Test
    fun `난이도를 내리면 점수도 내려간다`() {
        save(problemId, difficulty = "GOLD_5")
        // 올라간 것을 먼저 확인한다. 그러지 않으면 아무것도 반영되지 않는 구현에서도
        // 이 시험이 통과한다 — 시작값과 끝값이 같기 때문이다.
        assertEquals(goldScore to 1, scoreRepository.totalsOf(solverId))

        save(problemId, difficulty = "BRONZE_5")

        assertEquals(bronzeScore to 1, scoreRepository.totalsOf(solverId))
    }

    @Test
    fun `최고 점수는 난이도가 내려가도 따라 내려가지 않는다`() {
        save(problemId, difficulty = "GOLD_5")
        assertEquals(goldScore, peakScoreOf(solverId))

        save(problemId, difficulty = "BRONZE_5")

        // 실력 티어에 강등을 두지 않기로 한 것과 같은 규칙이다 (#58). 강등의 원인이
        // 자기 제출이 아니라 어드민의 조정인 상황을 만들지 않는다.
        assertEquals(goldScore, peakScoreOf(solverId))
        assertEquals(bronzeScore to 1, scoreRepository.totalsOf(solverId))
    }

    @Test
    fun `비공개로 돌리면 점수가 빠지고 다시 공개하면 돌아온다`() {
        // 계산식은 처음부터 published = true 를 봤다. 그 조건을 다시 확인하는 경로가
        // 없어서, 비공개로 돌린 문제의 점수가 그대로 남아 있었다.
        save(problemId, published = false)
        assertEquals(0 to 0, scoreRepository.totalsOf(solverId))

        save(problemId, published = true)
        assertEquals(bronzeScore to 1, scoreRepository.totalsOf(solverId))
    }

    @Test
    fun `문제를 지우면 점수가 빠진다`() {
        mockMvc.perform(
            delete("/api/v1/admin/problems/$problemId").header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isNoContent)

        assertEquals(0 to 0, scoreRepository.totalsOf(solverId))
    }

    @Test
    fun `다시 공개해도 처음 맞힌 시각은 그대로다`() {
        // 동점자 순서가 이것으로 갈린다 (#57). 다시 넣으면서 지금 시각으로 덮어쓰면
        // 어드민이 문제를 잠깐 내렸다 올린 것만으로 순위가 바뀐다.
        val solvedAt = solvedAtOf(solverId, problemId)

        save(problemId, published = false)
        save(problemId, published = true)

        assertEquals(solvedAt, solvedAtOf(solverId, problemId))
    }

    private fun createProblem(difficulty: String): Long {
        val response = mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(difficulty = difficulty, published = true)),
        )
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun save(id: Long, difficulty: String = "BRONZE_5", published: Boolean = true) {
        mockMvc.perform(
            put("/api/v1/admin/problems/$id")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(difficulty = difficulty, published = published)),
        ).andExpect(status().isOk)
    }

    private fun body(difficulty: String, published: Boolean) = """
        {
          "slug": "sum-two", "title": "두 수의 합",
          "category": "ALGORITHM", "difficulty": "$difficulty",
          "problemKind": "JUDGE_STDIO",
          "description": "두 정수를 더한다.", "timeLimitMs": 2000, "memoryLimitMb": 256,
          "published": $published,
          "testcases": [
            {"seq": 1, "input": "1 2\n", "expectedOutput": "3\n", "visibility": "PUBLIC"}
          ],
          "templates": []
        }
    """.trimIndent()

    private fun accept(userId: Long, problemId: Long) {
        val submissionId = jdbcClient.sql(
            """
            INSERT INTO submissions
                (user_id, problem_id, runtime_id, source_code, status, kind, created_at, updated_at)
            VALUES (:userId, :problemId, 'python:3.12', 'print(3)', 'PENDING', 'USER', now(), now())
            RETURNING id
            """,
        )
            .param("userId", userId)
            .param("problemId", problemId)
            .query(Long::class.java)
            .single()

        recorder.record(
            JudgeEventMessage(
                type = JudgeEventMessage.TYPE_COMPLETED,
                submissionId = submissionId,
                verdict = Verdict.ACCEPTED.name,
                passedCount = 1,
                totalCount = 1,
            ),
        )
    }

    private fun peakScoreOf(userId: Long): Int =
        jdbcClient.sql("SELECT peak_score FROM users WHERE id = :id")
            .param("id", userId)
            .query(Int::class.java)
            .single()

    private fun solvedAtOf(userId: Long, problemId: Long): String =
        jdbcClient.sql(
            "SELECT solved_at::text FROM user_problem_scores WHERE user_id = :userId AND problem_id = :problemId",
        )
            .param("userId", userId)
            .param("problemId", problemId)
            .query(String::class.java)
            .single()
}
