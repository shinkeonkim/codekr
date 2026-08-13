package codekr.api.problem

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Range
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 여러 파일을 완성하는 문제 (#457, #497).
 *
 * **설계를 물으려면 파일이 여럿이어야 한다.** 실행기 쪽은 PR #496 에서 섰고, 여기서는
 * 문제가 파일 목록을 갖고 제출이 그 파일들을 싣는 부분을 본다.
 */
class MultiFileProblemIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var redisTemplate: StringRedisTemplate
    @Autowired private lateinit var jdbcClient: JdbcClient

    private lateinit var adminToken: String
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        val admin = userRepository.save(
            User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.SUPERUSER)),
        )
        adminToken = tokenProvider.issueAccessToken(admin)
        val user = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER)))
        userToken = tokenProvider.issueAccessToken(user)
        create()
    }

    @Test
    fun `문제 상세에 그 언어로 채울 파일이 온다`() {
        // **파일 이름은 언어를 따라 갈린다.** 문제 단위로 내리면 화면이 언어를 바꿀 때
        // 무엇이 맞는 목록인지 알 수 없다.
        mockMvc.perform(get("/api/v1/problems/design-shapes"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runtimes[?(@.id == 'python:3.13')].files.length()").value(2))
            .andExpect(jsonPath("$.runtimes[?(@.id == 'python:3.13')].files[0].name").value("main.py"))
            .andExpect(jsonPath("$.runtimes[?(@.id == 'python:3.13')].files[1].editable").value(false))
    }

    @Test
    fun `여러 파일로 제출하면 파일이 채점 작업에 실린다`() {
        submit(
            """[{"name":"main.py","sourceCode":"from shape import area\nprint(area())"}]""",
        ).andExpect(status().isAccepted)

        val queued = queuedJob()
        assert(queued.contains("sourceFiles")) { "파일이 작업에 없다: $queued" }
        assert(queued.contains("from shape import area")) { "제출한 파일이 작업에 없다: $queued" }
        // **고칠 수 없는 파일은 문제의 것이 실린다** — 제출이 보내지 않아도 채점에는 있어야 한다.
        assert(queued.contains("def area")) { "고정 파일이 작업에 없다: $queued" }
    }

    @Test
    fun `고칠 수 없는 파일을 바꿔 보내도 문제의 것이 쓰인다`() {
        /*
          "이 인터페이스는 건드리지 말고 구현만 하라" 가 화면의 약속이면 API 를 직접
          부르는 순간 사라진다. **서버가 덮어쓴다.**
        */
        submit(
            """[{"name":"main.py","sourceCode":"print(1)"},{"name":"shape.py","sourceCode":"def area(): return 999"}]""",
        ).andExpect(status().isAccepted)

        val queued = queuedJob()
        assert(!queued.contains("999")) { "제출이 고정 파일을 바꿨다: $queued" }
    }

    @Test
    fun `빠진 파일이 있으면 거부한다`() {
        submit("""[]""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("빠진 파일")))
    }

    @Test
    fun `문제에 없는 파일 이름은 거부한다`() {
        // 실행기도 막지만(#457), 그 전에 여기서 걸러야 사용자가 이유를 안다.
        submit(
            """[{"name":"main.py","sourceCode":"print(1)"},{"name":"evil.py","sourceCode":"x"}]""",
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("없는 파일")))
    }

    @Test
    fun `파일 하나짜리 문제에 파일을 실어 보내면 거부한다`() {
        // 조용히 버리면 사용자는 자기가 쓴 것이 채점됐다고 믿는다.
        jdbcClient.sql("DELETE FROM problem_files").update()

        submit("""[{"name":"main.py","sourceCode":"print(1)"}]""")
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `파일 목록이 없는 문제는 지금과 같다`() {
        jdbcClient.sql("DELETE FROM problem_files").update()

        mockMvc.perform(
            post("/api/v1/problems/design-shapes/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.13","sourceCode":"print(1)"}"""),
        ).andExpect(status().isAccepted)
    }

    private fun queuedJob(): String =
        redisTemplate.opsForStream<String, String>()
            .range("codekr:judge:normal", Range.unbounded())
            .orEmpty()
            .joinToString(" ") { it.value.values.joinToString(" ") }

    private fun submit(files: String) = mockMvc.perform(
        post("/api/v1/problems/design-shapes/submissions")
            .header("Authorization", "Bearer $userToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"runtimeId":"python:3.13","files":$files}"""),
    )

    private fun create() {
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "slug": "design-shapes", "title": "도형 넓이",
                      "category": "ALGORITHM", "difficulty": "SILVER_5",
                      "description": "넓이를 구하는 함수를 쓰세요.", "published": true,
                      "allowedRuntimeIds": ["python:3.13"],
                      "testcases": [{"seq": 1, "input": "", "expectedOutput": "4\n", "visibility": "PUBLIC"}],
                      "templates": [],
                      "files": [
                        {"runtimeId": "python:3.13", "name": "main.py",
                         "template": "from shape import area\nprint(area())", "editable": true},
                        {"runtimeId": "python:3.13", "name": "shape.py",
                         "template": "def area():\n    return 4", "editable": false}
                      ]
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated)
    }
}
