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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 함수만 구현하는 문제 (#421).
 *
 * **하네스는 보이지 않는 코드다.** 새면 그 문제는 끝난다 — 하네스에는 입출력을 다루는
 * 방법과 때로는 정답의 모양이 들어 있다.
 *
 * 그리고 **하네스를 쓴 언어가 곧 허용 목록이다** (#419). 목록을 따로 고르게 하면
 * 두 곳이 어긋난다.
 */
class FunctionHarnessIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var redisTemplate: StringRedisTemplate

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
    }

    @Test
    fun `하네스는 문제 상세에 새지 않는다`() {
        create(harnessSource = "from solution import solve  # SECRET HARNESS")

        val body = mockMvc.perform(get("/api/v1/problems/two-sum-fn"))
            .andExpect(status().isOk)
            .andReturn().response.getContentAsString(Charsets.UTF_8)

        assert(!body.contains("SECRET HARNESS")) { "하네스가 샜다: $body" }
    }

    @Test
    fun `껍데기가 시작 코드로 온다`() {
        // **빈 화면에서 시작할 수 없는 유형이다** — 무엇을 채워야 하는지가 시작 코드다.
        create(template = "def solve(a, b):\n    pass\n")

        mockMvc.perform(get("/api/v1/problems/two-sum-fn"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runtimes[0].template").value("def solve(a, b):\n    pass\n"))
    }

    @Test
    fun `하네스를 쓴 언어만 그 문제를 풀 수 있다`() {
        /*
          **허용 목록을 따로 고르게 하지 않는다** (#419 를 그대로 쓴다). 두 곳이 같은
          것을 정하면 어긋나고, 어긋나면 "고를 수는 있는데 채점되지 않는" 조합이 생긴다.
        */
        create()

        mockMvc.perform(get("/api/v1/problems/two-sum-fn"))
            .andExpect(jsonPath("$.runtimes.length()").value(1))
            .andExpect(jsonPath("$.runtimes[0].id").value("python:3.13"))
            .andExpect(jsonPath("$.runtimeRestricted").value(true))
    }

    @Test
    fun `제출하면 그 언어의 하네스가 채점 작업에 실린다`() {
        // **채점기는 DB 를 읽지 않는다** (ADR-0004). 없으면 채점될 수 없다.
        create(harnessSource = "HARNESS FOR PYTHON")

        mockMvc.perform(
            post("/api/v1/problems/two-sum-fn/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.13","sourceCode":"def solve(a, b): return a + b"}"""),
        ).andExpect(status().isAccepted)

        val queued = redisTemplate.opsForStream<String, String>()
            .range("codekr:judge:normal", Range.unbounded())
            .orEmpty()
            .joinToString(" ") { it.value.values.joinToString(" ") }

        assert(queued.contains("HARNESS FOR PYTHON")) { "하네스가 작업에 없다: $queued" }
    }

    @Test
    fun `어드민 상세에는 하네스가 온다`() {
        // 고치려면 지금 무엇이 들어 있는지 보여야 한다. **어드민에게만** 간다.
        val id = create(harnessSource = "HARNESS BODY")

        mockMvc.perform(get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.harnesses[0].source").value("HARNESS BODY"))
            .andExpect(jsonPath("$.harnesses[0].runtimeId").value("python:3.13"))
    }

    private fun create(
        harnessSource: String = "from solution import solve",
        template: String = "def solve(a, b):\n    pass\n",
    ): Long {
        val response = mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "slug": "two-sum-fn", "title": "두 수의 합 (함수)",
                      "category": "ALGORITHM", "problemKind": "JUDGE_FUNCTION", "difficulty": "BRONZE_5",
                      "description": "solve 함수를 채우세요.", "published": true,
                      "harnesses": [
                        {"runtimeId": "python:3.13",
                         "source": ${quote(harnessSource)},
                         "template": ${quote(template)}}
                      ],
                      "testcases": [{"seq": 1, "input": "1 2\n", "expectedOutput": "3\n", "visibility": "PUBLIC"}],
                      "templates": []
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated).andReturn().response.getContentAsString(Charsets.UTF_8)
        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun quote(value: String) =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
