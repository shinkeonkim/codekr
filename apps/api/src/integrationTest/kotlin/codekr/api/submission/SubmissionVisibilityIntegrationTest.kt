package codekr.api.submission

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.entity.ProblemTestcase
import codekr.api.problem.entity.TestcaseVisibility
import codekr.api.problem.repository.ProblemRepository
import codekr.api.queue.message.JudgeEventMessage
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate

/**
 * 공개 범위 정책 (#33).
 *
 * 핵심은 **메타데이터와 소스 코드를 분리**한다는 것이다. 판정·실행 시간 같은 메타데이터는
 * 회원에게 보이지만, 소스 코드는 공개 옵션과 판정에 따라 가려진다.
 */
class SubmissionVisibilityIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var problemRepository: ProblemRepository
    @Autowired private lateinit var recorder: JudgeResultRecorder
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var ownerToken: String
    private lateinit var otherToken: String
    private lateinit var adminToken: String

    @BeforeEach
    fun setUp() {
        ownerToken = tokenProvider.issueAccessToken(
            userRepository.save(User("owner@codekr.dev", "x", "제출자", UserRole.USER)),
        )
        otherToken = tokenProvider.issueAccessToken(
            userRepository.save(User("other@codekr.dev", "x", "타인", UserRole.USER)),
        )
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", UserRole.ADMIN)),
        )
        transactionTemplate.executeWithoutResult {
            problemRepository.save(
                Problem(
                    slug = "two-sum", title = "두 수의 합",
                    category = ProblemCategory.ALGORITHM, difficultyLevel = Difficulty.BRONZE_5.level,
                    description = "설명", published = true,
                ),
            ).addTestcases(listOf(ProblemTestcase(1, "1 2\n", "3\n", TestcaseVisibility.PUBLIC)))
        }
    }

    @Test
    fun `기본 공개 범위는 비공개다`() {
        val id = submit()

        mockMvc.perform(get("/api/v1/submissions/$id").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.visibility").value("PRIVATE"))
    }

    @Test
    fun `비공개 제출은 남에게 소스가 보이지 않지만 메타데이터는 보인다`() {
        val id = submit()

        mockMvc.perform(get("/api/v1/submissions/$id").header("Authorization", "Bearer $otherToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sourceVisible").value(false))
            .andExpect(jsonPath("$.sourceCode").doesNotExist())
            // 누가 어떤 문제를 어떤 판정으로 풀었는지는 보인다 (#34 전체 목록의 전제).
            .andExpect(jsonPath("$.problemSlug").value("two-sum"))
            .andExpect(jsonPath("$.nickname").value("제출자"))
    }

    @Test
    fun `작성자와 관리자는 공개 범위와 무관하게 소스를 본다`() {
        val id = submit()

        for (token in listOf(ownerToken, adminToken)) {
            mockMvc.perform(get("/api/v1/submissions/$id").header("Authorization", "Bearer $token"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.sourceVisible").value(true))
                .andExpect(jsonPath("$.sourceCode").value("print(3)"))
        }
    }

    @Test
    fun `PUBLIC 제출은 판정과 무관하게 소스가 공개된다`() {
        val id = submit(visibility = "PUBLIC")

        mockMvc.perform(get("/api/v1/submissions/$id").header("Authorization", "Bearer $otherToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sourceVisible").value(true))
            .andExpect(jsonPath("$.sourceCode").value("print(3)"))
    }

    @Test
    fun `ACCEPTED_ONLY 는 채점 전과 오답에서는 비공개다`() {
        val id = submit(visibility = "ACCEPTED_ONLY")

        // 채점 전
        mockMvc.perform(get("/api/v1/submissions/$id").header("Authorization", "Bearer $otherToken"))
            .andExpect(jsonPath("$.sourceVisible").value(false))

        // 오답
        complete(id, verdict = "WRONG_ANSWER")
        mockMvc.perform(get("/api/v1/submissions/$id").header("Authorization", "Bearer $otherToken"))
            .andExpect(jsonPath("$.sourceVisible").value(false))
            .andExpect(jsonPath("$.sourceCode").doesNotExist())
    }

    @Test
    fun `ACCEPTED_ONLY 는 정답 확정 후 공개된다`() {
        val id = submit(visibility = "ACCEPTED_ONLY")
        complete(id, verdict = "ACCEPTED")

        mockMvc.perform(get("/api/v1/submissions/$id").header("Authorization", "Bearer $otherToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sourceVisible").value(true))
            .andExpect(jsonPath("$.sourceCode").value("print(3)"))
    }

    @Test
    fun `작성자는 제출 후 공개 범위를 바꿀 수 있다`() {
        val id = submit()

        mockMvc.perform(
            patch("/api/v1/submissions/$id/visibility")
                .header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"visibility":"PUBLIC"}"""),
        ).andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/submissions/$id").header("Authorization", "Bearer $otherToken"))
            .andExpect(jsonPath("$.sourceVisible").value(true))
    }

    @Test
    fun `남의 제출 공개 범위는 바꿀 수 없다`() {
        val id = submit()

        for (token in listOf(otherToken, adminToken)) {
            mockMvc.perform(
                patch("/api/v1/submissions/$id/visibility")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"visibility":"PUBLIC"}"""),
            ).andExpect(status().isForbidden)
        }
    }

    private fun complete(submissionId: Long, verdict: String) {
        recorder.record(
            JudgeEventMessage(
                type = "COMPLETED", submissionId = submissionId,
                verdict = verdict, passedCount = 1, totalCount = 1,
            ),
        )
    }

    private fun submit(visibility: String = "PRIVATE"): Long {
        val response = mockMvc.perform(
            post("/api/v1/problems/two-sum/submissions")
                .header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.12","sourceCode":"print(3)","visibility":"$visibility"}"""),
        ).andExpect(status().isAccepted).andReturn().response.contentAsString

        return Regex("\"submissionId\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }
}
