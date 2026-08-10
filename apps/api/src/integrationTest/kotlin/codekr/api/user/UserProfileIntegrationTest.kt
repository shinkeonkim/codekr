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

    private fun insertProblem(id: Long, slug: String, level: Int) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (:id, :slug, :slug, 'ALGORITHM', :level, '설명', true)
            """,
        ).param("id", id).param("slug", slug).param("level", level).update()
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
