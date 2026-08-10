package codekr.api.problem

import codekr.api.support.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** 문제 풀이 통계 (#84). */
class ProblemStatsIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var jdbcClient: JdbcClient

    @Test
    fun `제출 수가 아니라 제출한 사람 수로 센다`() {
        insertProblem()
        insertUser(1, "one@codekr.dev", "일번")
        insertUser(2, "two@codekr.dev", "이번")

        // 한 사람이 세 번 제출하고 그중 하나가 정답.
        repeat(2) { insertSubmission(userId = 1, verdict = "WRONG_ANSWER") }
        insertSubmission(userId = 1, verdict = "ACCEPTED")
        // 다른 한 사람은 한 번 제출해서 틀렸다.
        insertSubmission(userId = 2, verdict = "WRONG_ANSWER")

        mockMvc.perform(get("/api/v1/problems/two-sum"))
            .andExpect(status().isOk)
            // 제출은 4건이지만 사람은 2명이다.
            .andExpect(jsonPath("$.stats.submitterCount").value(2))
            .andExpect(jsonPath("$.stats.solverCount").value(1))
            .andExpect(jsonPath("$.stats.acceptanceRate").value(0.5))
    }

    @Test
    fun `아무도 제출하지 않았으면 정답률이 null 이다`() {
        insertProblem()

        mockMvc.perform(get("/api/v1/problems/two-sum"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.stats.submitterCount").value(0))
            // 0% 와 '아직 아무도 안 풀었음' 은 다른 사실이다.
            .andExpect(jsonPath("$.stats.acceptanceRate").doesNotExist())
    }

    @Test
    fun `정답 검증용 제출은 통계에 잡히지 않는다`() {
        insertProblem()
        insertUser(1, "admin@codekr.dev", "관리자")
        insertSubmission(userId = 1, verdict = "ACCEPTED", kind = "SOLUTION_VERIFICATION")

        mockMvc.perform(get("/api/v1/problems/two-sum"))
            .andExpect(status().isOk)
            // 어드민이 만든 검증 제출은 누군가 문제를 푼 것이 아니다.
            .andExpect(jsonPath("$.stats.submitterCount").value(0))
    }

    @Test
    fun `목록에도 같은 통계가 담긴다`() {
        insertProblem()
        insertUser(1, "one@codekr.dev", "일번")
        insertSubmission(userId = 1, verdict = "ACCEPTED")

        mockMvc.perform(get("/api/v1/problems"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].stats.solverCount").value(1))
    }

    private fun insertProblem() {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (1, 'two-sum', '두 수의 합', 'ALGORITHM', 1, '설명', true)
            """,
        ).update()
    }

    private fun insertUser(id: Long, email: String, nickname: String) {
        jdbcClient.sql(
            "INSERT INTO users (id, email, password_hash, nickname) VALUES (:id, :email, 'x', :nickname)",
        ).param("id", id).param("email", email).param("nickname", nickname).update()
    }

    private fun insertSubmission(userId: Long, verdict: String, kind: String = "USER") {
        jdbcClient.sql(
            """
            INSERT INTO submissions (user_id, problem_id, runtime_id, source_code, status, verdict, kind)
            VALUES (:userId, 1, 'python:3.12', 'print(3)', 'COMPLETED', :verdict, :kind)
            """,
        ).param("userId", userId).param("verdict", verdict).param("kind", kind).update()
    }
}
