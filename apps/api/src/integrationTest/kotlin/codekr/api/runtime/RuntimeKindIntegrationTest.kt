package codekr.api.runtime

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

/** 실행 환경은 문제 유형으로 갈린다 (#60). */
class RuntimeKindIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER)))
        token = tokenProvider.issueAccessToken(user)
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (1, 'two-sum', '두 수의 합', 'ALGORITHM', 1, '설명', true)
            """,
        ).update()
        jdbcClient.sql(
            """
            INSERT INTO problem_testcases (problem_id, seq, input, expected_output, visibility)
            VALUES (1, 1, '1 2', '3', 'HIDDEN')
            """,
        ).update()
    }

    @Test
    fun `기본 목록에 SQL 런타임이 섞이지 않는다`() {
        // 고를 수 있지만 채점은 되지 않는 조합을 만들지 않는다.
        mockMvc.perform(get("/api/v1/runtimes"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == 'sql:postgres16')]").isEmpty)
            .andExpect(jsonPath("$[?(@.id == 'python:3.12')]").isNotEmpty)
    }

    @Test
    fun `SQL 유형을 물으면 SQL 런타임만 나온다`() {
        // **SQL 은 하나가 아니다** (#454). 방언이 있으므로 DB 마다 런타임이 하나씩이고,
        // 어느 것으로 푸는지는 문제가 정한다(#419 의 허용 목록).
        mockMvc.perform(get("/api/v1/runtimes").param("problemKind", "JUDGE_SQL"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == 'sql:postgres16')]").isNotEmpty)
            .andExpect(jsonPath("$[?(@.id == 'sql:mysql8')]").isNotEmpty)
            .andExpect(jsonPath("$[?(@.monacoLanguage != 'sql')]").isEmpty)
    }

    @Test
    fun `유형이 맞지 않는 런타임으로는 제출할 수 없다`() {
        // 화면이 목록을 걸러 주지만, 화면을 거치지 않는 경로가 생겨도 막혀야 한다.
        mockMvc.perform(
            post("/api/v1/problems/two-sum/submissions")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"sql:postgres16","sourceCode":"SELECT 1;"}"""),
        ).andExpect(status().isNotFound)
    }
}
