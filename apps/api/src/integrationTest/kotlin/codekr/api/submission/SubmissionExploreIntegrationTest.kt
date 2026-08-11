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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate

class SubmissionExploreIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var problemRepository: ProblemRepository
    @Autowired private lateinit var recorder: JudgeResultRecorder
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var transactionTemplate: TransactionTemplate
    @Autowired private lateinit var jdbcClient: org.springframework.jdbc.core.simple.JdbcClient

    private lateinit var aliceToken: String
    private lateinit var bobToken: String
    private var alice: Long = 0

    @BeforeEach
    fun setUp() {
        aliceToken = tokenProvider.issueAccessToken(
            userRepository.save(User("alice@codekr.dev", "x", "앨리스", setOf(UserRole.USER)))
                .also { alice = it.id },
        )
        bobToken = tokenProvider.issueAccessToken(
            userRepository.save(User("bob@codekr.dev", "x", "밥", setOf(UserRole.USER))),
        )
        transactionTemplate.executeWithoutResult {
            listOf("two-sum", "reverse-words").forEach { slug ->
                problemRepository.save(
                    Problem(
                        slug = slug, title = slug,
                        category = ProblemCategory.ALGORITHM, difficultyLevel = Difficulty.BRONZE_5.level,
                        description = "설명", published = true,
                    ),
                ).addTestcases(listOf(ProblemTestcase(1, "1 2\n", "3\n", TestcaseVisibility.PUBLIC)))
            }
        }
    }

    @Test
    fun `다른 회원의 제출도 목록에 보인다`() {
        submit(aliceToken, "two-sum")
        submit(bobToken, "two-sum")

        mockMvc.perform(get("/api/v1/submissions/explore").header("Authorization", "Bearer $aliceToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            // 내 제출 목록(GET /submissions)은 여전히 내 것만 보여준다.
            .andExpect(jsonPath("$.content[*].nickname").value(org.hamcrest.Matchers.hasItem("밥")))
    }

    @Test
    fun `내 제출 목록은 여전히 내 것만 보여준다`() {
        submit(aliceToken, "two-sum")
        submit(bobToken, "two-sum")

        mockMvc.perform(get("/api/v1/submissions").header("Authorization", "Bearer $aliceToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `문제 회원 런타임 판정 필터가 각각 동작한다`() {
        val aliceId = submit(aliceToken, "two-sum")
        submit(bobToken, "reverse-words")
        complete(aliceId, "ACCEPTED")

        fun explore(query: String) =
            mockMvc.perform(get("/api/v1/submissions/explore?$query").header("Authorization", "Bearer $aliceToken"))
                .andExpect(status().isOk)

        explore("problemSlug=two-sum").andExpect(jsonPath("$.totalElements").value(1))
        explore("nickname=밥").andExpect(jsonPath("$.totalElements").value(1))
        explore("runtimeId=python:3.12").andExpect(jsonPath("$.totalElements").value(2))
        explore("runtimeId=ruby:3.4").andExpect(jsonPath("$.totalElements").value(0))
        explore("verdict=ACCEPTED").andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `필터를 조합해도 결과와 전체 건수가 일치한다`() {
        val aliceId = submit(aliceToken, "two-sum")
        submit(bobToken, "two-sum")
        complete(aliceId, "ACCEPTED")

        mockMvc.perform(
            get("/api/v1/submissions/explore?problemSlug=two-sum&nickname=앨리스&verdict=ACCEPTED")
                .header("Authorization", "Bearer $aliceToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].nickname").value("앨리스"))
    }

    @Test
    fun `제출일 범위 필터는 종료일 당일을 포함한다`() {
        submit(aliceToken, "two-sum")
        val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))

        mockMvc.perform(
            get("/api/v1/submissions/explore?from=$today&to=$today")
                .header("Authorization", "Bearer $aliceToken"),
        ).andExpect(status().isOk).andExpect(jsonPath("$.totalElements").value(1))

        mockMvc.perform(
            get("/api/v1/submissions/explore?from=${today.plusDays(1)}")
                .header("Authorization", "Bearer $aliceToken"),
        ).andExpect(status().isOk).andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `페이지 경계에서 중복이나 누락이 없다`() {
        // 제출 간격 제한(#189)을 피해 표에 바로 넣는다. 여기서 보는 것은 페이지 경계이지
        // 제출 경로가 아니다 — API 로 5번 내면 간격 제한에 걸린다.
        repeat(5) { insertSubmission(alice, "two-sum") }

        val firstPage = idsOf("size=2&page=0")
        val secondPage = idsOf("size=2&page=1")
        val thirdPage = idsOf("size=2&page=2")

        val all = firstPage + secondPage + thirdPage
        assert(all.size == 5) { "총 5건이어야 합니다: $all" }
        assert(all.toSet().size == 5) { "페이지 간 중복이 있습니다: $all" }
    }

    @Test
    fun `목록에는 소스 코드가 담기지 않는다`() {
        submit(bobToken, "two-sum")

        mockMvc.perform(get("/api/v1/submissions/explore").header("Authorization", "Bearer $aliceToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].sourceCode").doesNotExist())
            .andExpect(jsonPath("$.content[0].sourceVisible").value(false))
    }

    /** 제출 경로를 거치지 않고 제출 한 건을 남긴다. 목록·페이지 시험의 사전 준비용이다. */
    private fun insertSubmission(userId: Long, slug: String) {
        val problemId = problemRepository.findBySlugAndDeletedAtIsNull(slug)!!.id
        jdbcClient.sql(
            """
            INSERT INTO submissions
                (user_id, problem_id, runtime_id, source_code, status, kind, created_at, updated_at)
            VALUES (:userId, :problemId, 'python:3.12', 'print(3)', 'PENDING', 'USER', now(), now())
            """,
        ).param("userId", userId).param("problemId", problemId).update()
    }

    private fun idsOf(query: String): List<Int> {
        val body = mockMvc.perform(
            get("/api/v1/submissions/explore?$query").header("Authorization", "Bearer $aliceToken"),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return Regex("\"id\":(\\d+)").findAll(body).map { it.groupValues[1].toInt() }.toList()
    }

    private fun complete(submissionId: Long, verdict: String) {
        recorder.record(
            JudgeEventMessage(
                type = "COMPLETED", submissionId = submissionId,
                verdict = verdict, passedCount = 1, totalCount = 1,
            ),
        )
    }

    private fun submit(token: String, slug: String): Long {
        val response = mockMvc.perform(
            post("/api/v1/problems/$slug/submissions")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.12","sourceCode":"print(3)"}"""),
        ).andExpect(status().isAccepted).andReturn().response.contentAsString

        return Regex("\"submissionId\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }
}
