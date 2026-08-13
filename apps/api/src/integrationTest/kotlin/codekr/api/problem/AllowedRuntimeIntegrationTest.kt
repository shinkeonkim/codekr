package codekr.api.problem

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 문제마다 풀 수 있는 언어 (#419).
 *
 * 전에는 `problemKind` 하나로만 갈렸다 — 그것은 **문제의 종류**이지 언어 목록이 아니다.
 * 그래서 "파이썬으로만 푸는 문제" 를 낼 수 없었고, 아희·엄랭(#394)이 들어온 뒤로는
 * 모든 문제 목록에 그것이 떴다.
 */
class AllowedRuntimeIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private var userToken: String = ""

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("solver@codekr.dev", "x", "푸는사람", setOf(UserRole.USER)))
        userToken = tokenProvider.issueAccessToken(user)
    }

    @Test
    fun `비워 두면 그 종류의 전부를 허용한다`() {
        // **기존 문제가 그대로 돌아야 한다.** 언어를 새로 들일 때 문제를 전부 손보게
        // 하지 않으려는 것이기도 하다.
        problem(1, "free")

        val body = detail("free")
        assert(body.contains("python:3.12")) { "stdio 런타임이 전부 와야 한다: $body" }
    }

    @Test
    fun `좁혀 두면 그것만 내려간다`() {
        problem(2, "python-only")
        allow(2, "python:3.12")

        val body = detail("python-only")
        assert(body.contains("python:3.12")) { "허용한 런타임은 와야 한다: $body" }
        assert(!body.contains("\"id\":\"cpp")) { "허용하지 않은 런타임이 왔다: $body" }
    }

    @Test
    fun `허용하지 않은 언어로는 제출할 수 없다`() {
        /*
          **화면이 안 보여주는 것으로는 부족하다.** API 를 직접 부르면 통과한다 —
          그래서 서버가 막는다. 문구에 무엇을 쓸 수 있는지를 담는다: 목록이 짧은 것은
          고장이 아니라 출제자의 선택이다.
        */
        problem(3, "python-only-2")
        allow(3, "python:3.12")

        mockMvc.perform(
            post("/api/v1/problems/python-only-2/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"cpp:17","sourceCode":"int main(){}","visibility":"PRIVATE"}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("이 문제는 python:3.12 로만 풀 수 있습니다."))
    }

    @Test
    fun `허용한 언어로는 제출이 된다`() {
        problem(4, "python-only-3")
        allow(4, "python:3.12")

        mockMvc.perform(
            post("/api/v1/problems/python-only-3/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.12","sourceCode":"print(3)","visibility":"PRIVATE"}"""),
        ).andExpect(status().isAccepted)
    }

    @Test
    fun `허용 목록이 비어 있으면 아무 언어로나 제출된다`() {
        problem(5, "free-2")

        mockMvc.perform(
            post("/api/v1/problems/free-2/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"cpp:17","sourceCode":"int main(){}","visibility":"PRIVATE"}"""),
        ).andExpect(status().isAccepted)
    }

    @Test
    fun `종류가 다른 런타임은 허용 목록과 무관하게 막힌다`() {
        // #60 의 규칙이 먼저다. 허용 목록은 **그 안에서** 좁히는 것이지 넓히는 것이 아니다.
        problem(6, "stdio-only")
        allow(6, "sql:postgres16")

        mockMvc.perform(
            post("/api/v1/problems/stdio-only/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"sql:postgres16","sourceCode":"SELECT 1","visibility":"PRIVATE"}"""),
        ).andExpect(status().isNotFound)
    }

    private fun problem(id: Long, slug: String) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (:id, :slug, '문제', 'ALGORITHM', 5, '설명', true)
            """,
        ).param("id", id).param("slug", slug).update()
        // 테스트케이스가 없으면 제출 자체가 막힌다 (#60) — 이 시험이 볼 것은 그것이 아니다.
        jdbcClient.sql(
            """
            INSERT INTO problem_testcases (problem_id, seq, input, expected_output, visibility)
            VALUES (:id, 1, '1 2', '3', 'PUBLIC')
            """,
        ).param("id", id).update()
    }

    private fun allow(problemId: Long, runtimeId: String) {
        jdbcClient.sql(
            "INSERT INTO problem_allowed_runtimes (problem_id, runtime_id) VALUES (:p, :r)",
        ).param("p", problemId).param("r", runtimeId).update()
    }

    private fun detail(slug: String): String =
        mockMvc.perform(get("/api/v1/problems/$slug"))
            .andExpect(status().isOk)
            .andReturn().response.getContentAsString(Charsets.UTF_8)
}
