package codekr.api.problem.admin

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.queue.message.JudgeEventMessage
import codekr.api.submission.repository.SubmissionRepository
import codekr.api.submission.service.JudgeResultRecorder
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class SolutionVerificationIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var submissionRepository: SubmissionRepository
    @Autowired private lateinit var recorder: JudgeResultRecorder
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var adminToken: String
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.ADMIN))),
        )
        userToken = tokenProvider.issueAccessToken(
            userRepository.save(User("member@codekr.dev", "x", "일반유저", setOf(UserRole.USER))),
        )
    }

    @Test
    fun `정답 코드 없이도 문제를 등록할 수 있다`() {
        val id = createProblem(withSolution = false)

        mockMvc.perform(get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.solution").doesNotExist())
            .andExpect(jsonPath("$.verification").doesNotExist())
    }

    @Test
    fun `정답 코드가 없으면 검증을 시작할 수 없다`() {
        val id = createProblem(withSolution = false)

        mockMvc.perform(post("/api/v1/admin/problems/$id/verify").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("SOLUTION_REQUIRED"))
    }

    @Test
    fun `검증을 시작하면 채점 제출이 만들어지고 결과가 문제 상세에 실린다`() {
        val id = createProblem(withSolution = true)

        val response = mockMvc.perform(
            post("/api/v1/admin/problems/$id/verify").header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isAccepted)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.stale").value(false))
            .andReturn().response.contentAsString

        val submissionId = Regex("\"submissionId\":(\\d+)").find(response)!!.groupValues[1].toLong()
        completeJudging(submissionId, verdict = "ACCEPTED", passed = 2, total = 2)

        mockMvc.perform(get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verification.verdict").value("ACCEPTED"))
            .andExpect(jsonPath("$.verification.passedCount").value(2))
            .andExpect(jsonPath("$.verification.stale").value(false))
            .andExpect(jsonPath("$.verification.results.length()").value(1))
    }

    @Test
    fun `테스트케이스를 고치면 이전 검증 결과가 낡은 것으로 표시된다`() {
        val id = createProblem(withSolution = true)
        val response = mockMvc.perform(
            post("/api/v1/admin/problems/$id/verify").header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isAccepted).andReturn().response.contentAsString
        val submissionId = Regex("\"submissionId\":(\\d+)").find(response)!!.groupValues[1].toLong()
        completeJudging(submissionId, verdict = "ACCEPTED", passed = 2, total = 2)

        // 기대 출력을 바꾸면 이전 검증은 더 이상 이 문제에 대한 결과가 아니다.
        mockMvc.perform(
            put("/api/v1/admin/problems/$id")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(withSolution = true, expectedOutput = "999\\n")),
        ).andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verification.stale").value(true))
    }

    @Test
    fun `일반 사용자는 정답 코드도 검증 제출도 볼 수 없다`() {
        val id = createProblem(withSolution = true)
        val response = mockMvc.perform(
            post("/api/v1/admin/problems/$id/verify").header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isAccepted).andReturn().response.contentAsString
        val submissionId = Regex("\"submissionId\":(\\d+)").find(response)!!.groupValues[1].toLong()

        // 어드민 API 자체가 막힌다.
        mockMvc.perform(get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $userToken"))
            .andExpect(status().isForbidden)

        // 검증 제출은 존재 자체를 알리지 않는다 (403 이 아니라 404).
        mockMvc.perform(get("/api/v1/submissions/$submissionId").header("Authorization", "Bearer $userToken"))
            .andExpect(status().isNotFound)

        // 공개 문제 상세에도 정답 코드가 없다.
        mockMvc.perform(get("/api/v1/problems/verify-me"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.solution").doesNotExist())
            .andExpect(jsonPath("$.solutionSourceCode").doesNotExist())
    }

    @Test
    fun `검증 제출은 어드민의 제출 목록에도 섞이지 않는다`() {
        val id = createProblem(withSolution = true)
        mockMvc.perform(post("/api/v1/admin/problems/$id/verify").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isAccepted)

        mockMvc.perform(get("/api/v1/submissions").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `정답 코드를 바꾸면 이전 검증 결과가 사라진다`() {
        val id = createProblem(withSolution = true)
        mockMvc.perform(post("/api/v1/admin/problems/$id/verify").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isAccepted)

        mockMvc.perform(
            put("/api/v1/admin/problems/$id")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(withSolution = true, solutionCode = "print(0)")),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.verification").doesNotExist())
    }

    private fun completeJudging(submissionId: Long, verdict: String, passed: Int, total: Int) {
        recorder.record(JudgeEventMessage(type = "JUDGING", submissionId = submissionId, totalCount = total))
        recorder.record(
            JudgeEventMessage(
                type = "TESTCASE", submissionId = submissionId, seq = 1,
                verdict = "ACCEPTED", runtimeMs = 10, memoryKb = 4096,
            ),
        )
        recorder.record(
            JudgeEventMessage(
                type = "COMPLETED", submissionId = submissionId,
                verdict = verdict, passedCount = passed, totalCount = total,
            ),
        )
        submissionRepository.flush()
    }

    private fun createProblem(withSolution: Boolean): Long {
        val response = mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(withSolution)),
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun body(
        withSolution: Boolean,
        expectedOutput: String = "3\\n",
        solutionCode: String = "print(3)",
    ): String {
        val solution = if (withSolution) {
            """, "solution": {"runtimeId": "python:3.12", "sourceCode": "$solutionCode"}"""
        } else {
            ""
        }
        return """
            {
              "slug": "verify-me", "title": "검증 대상",
              "category": "ALGORITHM", "difficulty": "BRONZE_5",
              "description": "설명", "published": true,
              "testcases": [
                {"seq": 1, "input": "1 2\n", "expectedOutput": "$expectedOutput", "visibility": "PUBLIC"},
                {"seq": 2, "input": "1 2\n", "expectedOutput": "$expectedOutput", "visibility": "HIDDEN"}
              ]$solution
            }
        """.trimIndent()
    }
}
