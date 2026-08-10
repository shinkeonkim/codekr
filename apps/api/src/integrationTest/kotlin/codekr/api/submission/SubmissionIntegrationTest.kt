package codekr.api.submission

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.entity.ProblemTestcase
import codekr.api.problem.entity.TestcaseVisibility
import codekr.api.problem.repository.ProblemRepository
import codekr.api.queue.message.JudgeEventMessage
import codekr.api.submission.entity.SubmissionStatus
import codekr.api.submission.repository.SubmissionRepository
import codekr.api.submission.repository.SubmissionTestcaseResultRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals

class SubmissionIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var problemRepository: ProblemRepository
    @Autowired private lateinit var submissionRepository: SubmissionRepository
    @Autowired private lateinit var resultRepository: SubmissionTestcaseResultRepository
    @Autowired private lateinit var recorder: JudgeResultRecorder
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var ownerToken: String
    private lateinit var otherToken: String

    @BeforeEach
    fun setUp() {
        ownerToken = tokenProvider.issueAccessToken(
            userRepository.save(User("owner@codekr.dev", "x", "제출자", UserRole.USER)),
        )
        otherToken = tokenProvider.issueAccessToken(
            userRepository.save(User("other@codekr.dev", "x", "타인", UserRole.USER)),
        )
        transactionTemplate.executeWithoutResult {
            problemRepository.save(
                Problem(
                    slug = "two-sum", title = "두 수의 합",
                    category = ProblemCategory.ALGORITHM, difficultyLevel = Difficulty.BRONZE_5.level,
                    description = "설명", published = true,
                ),
            ).addTestcases(
                listOf(
                    ProblemTestcase(1, "1 2\n", "3\n", TestcaseVisibility.PUBLIC),
                    ProblemTestcase(2, "10 20\n", "30\n", TestcaseVisibility.HIDDEN),
                ),
            )
        }
    }

    @Test
    fun `제출하면 PENDING 상태로 접수되고 테스트케이스 수가 기록된다`() {
        val submission = submissionRepository.findById(submit()).orElseThrow()

        assertEquals(SubmissionStatus.PENDING, submission.status)
        assertEquals(2, submission.totalCount)
    }

    @Test
    fun `남의 제출은 조회할 수 없다`() {
        val submissionId = submit()

        mockMvc.perform(get("/api/v1/submissions/$submissionId").header("Authorization", "Bearer $otherToken"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `채점 이벤트가 제출 상태와 테스트케이스 결과에 반영된다`() {
        val submissionId = submit()

        recorder.record(JudgeEventMessage(type = "JUDGING", submissionId = submissionId, totalCount = 2))
        recorder.record(
            JudgeEventMessage(
                type = "TESTCASE", submissionId = submissionId, seq = 1,
                verdict = "ACCEPTED", runtimeMs = 12, memoryKb = 5000,
            ),
        )
        recorder.record(
            JudgeEventMessage(
                type = "COMPLETED", submissionId = submissionId,
                verdict = "WRONG_ANSWER", passedCount = 1, totalCount = 2, maxRuntimeMs = 12, maxMemoryKb = 5000,
            ),
        )

        mockMvc.perform(get("/api/v1/submissions/$submissionId").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.verdict").value("WRONG_ANSWER"))
            .andExpect(jsonPath("$.passedCount").value(1))
            .andExpect(jsonPath("$.results.length()").value(1))
    }

    @Test
    fun `같은 테스트케이스 이벤트가 두 번 와도 결과가 중복되지 않는다`() {
        val submissionId = submit()
        val event = JudgeEventMessage(
            type = "TESTCASE", submissionId = submissionId, seq = 1,
            verdict = "ACCEPTED", runtimeMs = 12, memoryKb = 5000,
        )

        recorder.record(event)
        recorder.record(event.copy(runtimeMs = 30))

        val results = resultRepository.findBySubmissionIdOrderBySeqAsc(submissionId)
        assertEquals(1, results.size)
        assertEquals(30, results.first().runtimeMs)
    }

    @Test
    fun `문제가 삭제되어도 제출 이력은 남는다`() {
        val submissionId = submit()
        transactionTemplate.executeWithoutResult {
            problemRepository.findBySlugAndDeletedAtIsNull("two-sum")!!.delete()
        }

        mockMvc.perform(get("/api/v1/submissions/$submissionId").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.problemTitle").value("두 수의 합"))
    }

    @Test
    fun `지원하지 않는 런타임으로는 제출할 수 없다`() {
        mockMvc.perform(
            post("/api/v1/problems/two-sum/submissions")
                .header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"ruby:3.3","sourceCode":"puts 1"}"""),
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("RUNTIME_NOT_FOUND"))
    }

    private fun submit(): Long {
        val response = mockMvc.perform(
            post("/api/v1/problems/two-sum/submissions")
                .header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.12","sourceCode":"print(3)"}"""),
        ).andExpect(status().isAccepted).andReturn().response.contentAsString

        return Regex("\"submissionId\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }
}
