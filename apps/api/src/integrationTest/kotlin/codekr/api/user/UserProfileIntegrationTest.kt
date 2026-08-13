package codekr.api.user

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

/** 회원 프로필 (#83). */
class UserProfileIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private var targetId: Long = 0
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        targetId = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER))).id
        token = tokenProvider.issueAccessToken(
            userRepository.save(User("viewer@codekr.dev", "x", "구경꾼", setOf(UserRole.USER))),
        )
        insertProblem(1, "two-sum", level = 1) // 브론즈 5
        insertProblem(2, "reverse", level = 9) // 실버 2
    }

    @Test
    fun `푼 문제 수는 제출 수가 아니라 문제 수다`() {
        // 같은 문제를 세 번 맞혔다.
        repeat(3) { insertSubmission(problemId = 1, verdict = "ACCEPTED") }
        insertSubmission(problemId = 2, verdict = "WRONG_ANSWER")

        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nickname").value("풀이왕"))
            .andExpect(jsonPath("$.solvedCount").value(1))
            .andExpect(jsonPath("$.submissionCount").value(4))
    }

    @Test
    fun `푼 문제의 난이도를 티어로 묶어 보여준다`() {
        insertSubmission(problemId = 1, verdict = "ACCEPTED")
        insertSubmission(problemId = 2, verdict = "ACCEPTED")

        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.solvedByTier.length()").value(2))
            .andExpect(jsonPath("$.solvedByTier[0].tier").value("BRONZE"))
            .andExpect(jsonPath("$.solvedByTier[0].solvedCount").value(1))
            .andExpect(jsonPath("$.solvedByTier[1].tier").value("SILVER"))
    }

    @Test
    fun `비공개로 돌린 문제는 난이도 분포에서 빠진다`() {
        // 점수는 published 를 보는데 분포는 보지 않아, 어드민이 문제를 내리면 같은
        // 화면의 두 숫자가 다른 집합을 말했다 (#269).
        insertProblem(3, "hidden", level = 9, published = false)
        insertSubmission(problemId = 1, verdict = "ACCEPTED")
        insertSubmission(problemId = 3, verdict = "ACCEPTED")

        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            // 실버(레벨 9)가 통째로 사라져야 한다 — 브론즈 하나만 남는다.
            .andExpect(jsonPath("$.solvedByTier.length()").value(1))
            .andExpect(jsonPath("$.solvedByTier[0].tier").value("BRONZE"))
            // 푼 문제 수도 같은 기준이어야 한다.
            .andExpect(jsonPath("$.solvedCount").value(1))
    }

    @Test
    fun `이메일은 프로필에 담기지 않는다`() {
        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").doesNotExist())
    }

    @Test
    fun `없는 닉네임은 404 다`() {
        mockMvc.perform(get("/api/v1/users/없는사람").header("Authorization", "Bearer $token"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `정답 검증용 제출은 프로필 집계에 잡히지 않는다`() {
        insertSubmission(problemId = 1, verdict = "ACCEPTED", kind = "SOLUTION_VERIFICATION")

        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.solvedCount").value(0))
            .andExpect(jsonPath("$.submissionCount").value(0))
    }

    @Test
    fun `로그인 없이 프로필이 열린다`() {
        /*
          게시판·랭킹·문제집이 이름을 공개로 보여 주고 링크를 건다 (#333).
          **누르면 튕기는 링크는 고장으로 보인다.**
        */
        insertSubmission(problemId = 1, verdict = "ACCEPTED")

        mockMvc.perform(get("/api/v1/users/풀이왕"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nickname").value("풀이왕"))
            .andExpect(jsonPath("$.solvedCount").value(1))
    }

    @Test
    fun `활동 그래프도 함께 열린다`() {
        // 프로필만 열고 이것을 막으면 페이지의 절반이 비는데, 비로그인은 그것이
        // 원래 비어 있는 것인지 고장인지 알 수 없다.
        mockMvc.perform(get("/api/v1/users/풀이왕/activity")).andExpect(status().isOk)
    }

    @Test
    fun `제출 목록까지 열리지는 않는다`() {
        /*
          **선은 "센 숫자냐 한 줄 한 줄이냐" 로 긋는다.**

          프로필이 주는 것은 개수이고, 어떤 문제를 어떤 결과로 냈는지는 여기서
          나오지 않는다. 그 목록(#34)이 열려 있지 않다는 것이 이 결정의 전제다 —
          이 시험이 깨지면 프로필 공개의 근거가 함께 무너진다.
        */
        mockMvc.perform(get("/api/v1/submissions")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `이메일은 비로그인에게도 로그인에게도 나가지 않는다`() {
        val body = mockMvc.perform(get("/api/v1/users/풀이왕")).andReturn().response.contentAsString

        kotlin.test.assertTrue(!body.contains("solver@codekr.dev"), "이메일이 새면 안 됩니다: $body")
    }

    private fun insertProblem(id: Long, slug: String, level: Int, published: Boolean = true) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (:id, :slug, :slug, 'ALGORITHM', :level, '설명', :published)
            """,
        )
            .param("id", id)
            .param("slug", slug)
            .param("level", level)
            .param("published", published)
            .update()
    }

    private fun insertSubmission(problemId: Long, verdict: String, kind: String = "USER") {
        jdbcClient.sql(
            """
            INSERT INTO submissions (user_id, problem_id, runtime_id, source_code, status, verdict, kind)
            VALUES (:userId, :problemId, 'python:3.12', 'print(3)', 'COMPLETED', :verdict, :kind)
            """,
        )
            .param("userId", targetId)
            .param("problemId", problemId)
            .param("verdict", verdict)
            .param("kind", kind)
            .update()
    }
}
