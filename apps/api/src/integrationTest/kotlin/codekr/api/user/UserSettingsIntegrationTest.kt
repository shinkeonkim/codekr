package codekr.api.user

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** 사용자 설정과 기본 공개 범위 (#104). */
class UserSettingsIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        token = tokenProvider.issueAccessToken(
            userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER))),
        )
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
    fun `기본값은 비공개다`() {
        // 설정을 도입했다고 기존 동작이 바뀌면 안 된다.
        mockMvc.perform(get("/api/v1/users/me/settings").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.defaultSubmissionVisibility").value("PRIVATE"))
    }

    @Test
    fun `설정한 기본값이 제출에 적용된다`() {
        changeDefault("PUBLIC")

        // 제출에 visibility 를 넣지 않는다.
        submit(body = """{"runtimeId":"python:3.12","sourceCode":"print(3)"}""")

        assertVisibility("PUBLIC")
    }

    @Test
    fun `제출에서 지정한 값이 기본값을 이긴다`() {
        changeDefault("PUBLIC")

        submit(body = """{"runtimeId":"python:3.12","sourceCode":"print(3)","visibility":"PRIVATE"}""")

        // 기본값이 있어도 제출마다 고를 수 있어야 한다.
        assertVisibility("PRIVATE")
    }

    @Test
    fun `명시적으로 비공개를 고른 것과 안 고른 것을 구분한다`() {
        changeDefault("ACCEPTED_ONLY")

        submit(body = """{"runtimeId":"python:3.12","sourceCode":"print(3)","visibility":"PRIVATE"}""")

        // PRIVATE 를 요청 기본값으로 뒀다면 여기서 ACCEPTED_ONLY 가 되어 버린다.
        assertVisibility("PRIVATE")
    }

    @Test
    fun `보내지 않은 항목은 바뀌지 않는다`() {
        changeDefault("PUBLIC")

        mockMvc.perform(
            patch("/api/v1/users/me/settings")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.defaultSubmissionVisibility").value("PUBLIC"))
    }

    @Test
    fun `기본값을 바꿔도 이미 낸 제출은 그대로다`() {
        submit(body = """{"runtimeId":"python:3.12","sourceCode":"print(3)"}""")
        changeDefault("PUBLIC")

        // 소급 적용하지 않는다. 이미 낸 제출의 공개 범위가 몰래 바뀌면 안 된다.
        assertVisibility("PRIVATE")
    }

    private fun changeDefault(visibility: String) {
        mockMvc.perform(
            patch("/api/v1/users/me/settings")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"defaultSubmissionVisibility":"$visibility"}"""),
        ).andExpect(status().isOk)
    }

    private fun submit(body: String) {
        mockMvc.perform(
            post("/api/v1/problems/two-sum/submissions")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        ).andExpect(status().isAccepted)
    }

    private fun assertVisibility(expected: String) {
        val actual = jdbcClient.sql("SELECT visibility FROM submissions ORDER BY id DESC LIMIT 1")
            .query(String::class.java)
            .single()
        kotlin.test.assertEquals(expected, actual)
    }
}
