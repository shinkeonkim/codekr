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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 함수만 구현하는 문제 (#446, #421).
 *
 * **하네스는 절대 사용자에게 보이면 안 된다** — 정답의 일부나 판정 방식이 들어간다.
 * 그리고 **하네스를 쓴 언어가 곧 풀 수 있는 언어다** (#419 를 그대로 쓴다).
 */
class FunctionProblemIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var redisTemplate: org.springframework.data.redis.core.StringRedisTemplate

    private var setterToken: String = ""
    private var userToken: String = ""

    @BeforeEach
    fun setUp() {
        val setter = userRepository.save(
            User("setter@codekr.dev", "x", "출제자", setOf(UserRole.USER, UserRole.PROBLEM_SETTER)),
        )
        setterToken = tokenProvider.issueAccessToken(setter)
        val solver = userRepository.save(User("solver@codekr.dev", "x", "푸는사람", setOf(UserRole.USER)))
        userToken = tokenProvider.issueAccessToken(solver)
    }

    @Test
    fun `하네스를 쓴 언어로만 풀 수 있다`() {
        create(slug = "add-function", harnesses = """{"python:3.13": "from solution import solve"}""")

        mockMvc.perform(get("/api/v1/problems/add-function"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runtimeRestricted").value(true))
            .andExpect(jsonPath("$.runtimes.length()").value(1))
            .andExpect(jsonPath("$.runtimes[0].id").value("python:3.13"))
    }

    @Test
    fun `하네스는 문제 상세에 새지 않는다`() {
        /*
          **이것이 무너지면 문제가 무너진다.** 하네스에는 기대값·판정 방식이 들어간다 —
          필드를 안 넣었다는 것만으로는 부족해서, 응답 본문에 그 문자열이 없는지 직접 본다.
        */
        create(
            slug = "secret-harness",
            harnesses = """{"python:3.13": "SECRET_EXPECTED = [7, 0, -2000]"}""",
        )

        val body = mockMvc.perform(get("/api/v1/problems/secret-harness"))
            .andReturn().response.getContentAsString(Charsets.UTF_8)

        assert(!body.contains("SECRET_EXPECTED")) { "하네스가 샜다: $body" }
        // 필드 자체가 없어야 한다. (slug 에도 harness 가 들어 있으므로 필드 이름으로 본다)
        assert(!body.contains("\"harnesses\"")) { "하네스 자리가 응답에 있다: $body" }
    }

    @Test
    fun `하네스가 없는 언어로는 제출할 수 없다`() {
        create(slug = "python-function", harnesses = """{"python:3.13": "from solution import solve"}""")

        mockMvc.perform(
            post("/api/v1/problems/python-function/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"cpp:17","sourceCode":"int main(){}","visibility":"PRIVATE"}"""),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `하네스가 없으면 공개할 수 없다`() {
        // 허용 언어가 하네스로 정해지므로, 없으면 **아무도 풀 수 없는 문제**가 된다.
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $setterToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(slug = "no-harness", harnesses = "{}")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("하네스")))
    }

    @Test
    fun `함수형이 아닌 문제에는 하네스를 실을 수 없다`() {
        // 유형별 자료는 그 유형에만 실린다 (#60 이 SQL 에서 정한 규칙과 같다).
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $setterToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(slug = "stdio-with-harness", harnesses = """{"python:3.13": "x"}""")
                        .replace("JUDGE_FUNCTION", "JUDGE_STDIO"),
                ),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `실행기가 방식을 모르는 언어는 하네스를 받지 않는다`() {
        // `runtimes.yaml` 에 `functionHarness` 가 없는 런타임이다 — 하네스를 줘도 못 돌린다.
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $setterToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(slug = "cpp-function", harnesses = """{"cpp:17": "int main(){}"}""")),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `허용 언어를 따로 고르게 하지 않는다`() {
        // 두 곳이 같은 것을 정하면 어긋난다 — 하네스가 곧 허용 목록이다.
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $setterToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(slug = "both", harnesses = """{"python:3.13": "x"}""")
                        .replace("\"allowedRuntimeIds\": []", "\"allowedRuntimeIds\": [\"python:3.13\"]"),
                ),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `제출하면 그 언어의 하네스가 채점 작업에 실린다`() {
        /*
          **채점기가 DB 를 읽지 않는다** (ADR-0004). 그래서 하네스도 작업에 실려 가야
          하고, 실리지 않으면 채점기는 하네스 없이 함수만 있는 코드를 돌리게 된다.
        */
        create(slug = "carried", harnesses = """{"python:3.13": "HARNESS FOR PYTHON"}""")

        mockMvc.perform(
            post("/api/v1/problems/carried/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.13","sourceCode":"def solve(a,b): return a+b","visibility":"PRIVATE"}"""),
        ).andExpect(status().isAccepted)

        val queued = redisTemplate.opsForStream<String, String>()
            .range("codekr:judge:normal", org.springframework.data.domain.Range.unbounded())
            .orEmpty()
            .joinToString(" ") { it.value.values.joinToString(" ") }

        assert(queued.contains("HARNESS FOR PYTHON")) { "하네스가 채점 작업에 없습니다: $queued" }
    }

    @Test
    fun `어드민 상세에는 하네스가 온다`() {
        // 고치려면 지금 무엇이 들어 있는지 보여야 한다. **어드민에게만** 간다.
        val id = create(slug = "editable", harnesses = """{"python:3.13": "HARNESS BODY"}""")

        mockMvc.perform(
            get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $setterToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.harnesses['python:3.13']").value("HARNESS BODY"))
    }

    private fun create(slug: String, harnesses: String): Long {
        val response = mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $setterToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(slug, harnesses)),
        ).andExpect(status().isCreated).andReturn().response.getContentAsString(Charsets.UTF_8)
        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun body(slug: String, harnesses: String): String =
        """
        {
          "slug": "$slug",
          "title": "두 수를 더하는 함수",
          "category": "ALGORITHM",
          "problemKind": "JUDGE_FUNCTION",
          "description": "solve(a, b) 를 구현하세요.",
          "inputDescription": "정수 둘",
          "outputDescription": "합",
          "timeLimitMs": 2000,
          "memoryLimitMb": 256,
          "published": true,
          "allowedRuntimeIds": [],
          "harnesses": $harnesses,
          "testcases": [
            {"seq": 1, "input": "1 2\n", "expectedOutput": "3\n", "visibility": "PUBLIC"}
          ]
        }
        """.trimIndent()
}
