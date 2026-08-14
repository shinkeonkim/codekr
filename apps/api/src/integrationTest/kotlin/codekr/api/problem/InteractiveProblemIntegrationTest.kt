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
 * 인터랙티브 문제 (#474).
 *
 * **대화를 주관하는 코드는 정답의 일부다** — 사용자에게 절대 내려가지 않는다.
 * 그리고 그 코드가 없으면 아무도 못 푸는 문제가 된다.
 */
class InteractiveProblemIntegrationTest : IntegrationTestBase() {

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
    fun `채점 코드는 문제 상세에 새지 않는다`() {
        create(interactor = "SECRET = 42")

        val body = mockMvc.perform(get("/api/v1/problems/guess-it"))
            .andExpect(status().isOk)
            .andReturn().response.getContentAsString(Charsets.UTF_8)

        assert(!body.contains("SECRET")) { "채점 코드가 샜다: $body" }
        assert(!body.contains("interactor")) { "채점 코드 자리가 응답에 있다: $body" }
    }

    @Test
    fun `채점 코드 없이 인터랙티브 문제를 만들 수 없다`() {
        // 없으면 아무도 못 푸는 문제가 된다 — 무엇을 물어도 답할 것이 없다.
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("no-interactor", interactor = null)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("채점 코드")))
    }

    @Test
    fun `다른 유형에 채점 코드를 실을 수 없다`() {
        // 유형별 자료는 그 유형에만 실린다 (#60 이 SQL 에서 정한 규칙과 같다).
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body("stdio-with-interactor", interactor = "x")
                        .replace("\"problemKind\": \"JUDGE_INTERACTIVE\"", "\"problemKind\": \"JUDGE_STDIO\""),
                ),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `제출하면 채점 코드가 채점 작업에 실린다`() {
        // **채점기가 DB 를 읽지 않는다** (ADR-0004). 실리지 않으면 대화할 상대가 없다.
        create(interactor = "INTERACTOR SOURCE HERE")

        mockMvc.perform(
            post("/api/v1/problems/guess-it/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"interactive:python3.13","sourceCode":"print(1, flush=True)"}"""),
        ).andExpect(status().isAccepted)

        val queued = redisTemplate.opsForStream<String, String>()
            .range("codekr:judge:normal", Range.unbounded())
            .orEmpty()
            .joinToString(" ") { it.value.values.joinToString(" ") }

        assert(queued.contains("INTERACTOR SOURCE HERE")) { "채점 코드가 작업에 없다: $queued" }
    }

    @Test
    fun `인터랙티브 문제 화면에는 그 실행 환경만 보인다`() {
        // 파이썬으로 시작한다 (#474) — 둘이 같은 컨테이너에서 동시에 돌아야 한다.
        create(interactor = "x")

        mockMvc.perform(get("/api/v1/problems/guess-it"))
            .andExpect(jsonPath("$.runtimes.length()").value(1))
            .andExpect(jsonPath("$.runtimes[0].id").value("interactive:python3.13"))
    }

    @Test
    fun `어드민 상세에는 채점 코드가 온다`() {
        // 고치려면 지금 무엇이 들어 있는지 보여야 한다. **어드민에게만** 간다.
        val id = create(interactor = "INTERACTOR BODY")

        mockMvc.perform(get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.interactorSource").value("INTERACTOR BODY"))
    }

    private fun create(interactor: String?): Long {
        val response = mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("guess-it", interactor)),
        ).andExpect(status().isCreated).andReturn().response.getContentAsString(Charsets.UTF_8)
        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun body(slug: String, interactor: String?) = """
        {
          "slug": "$slug", "title": "숨은 수 맞히기",
          "category": "ALGORITHM", "problemKind": "JUDGE_INTERACTIVE", "difficulty": "SILVER_5",
          "description": "스무 번 안에 맞히세요.", "published": true,
          "timeLimitMs": 5000, "memoryLimitMb": 256,
          ${if (interactor == null) "" else "\"interactorSource\": \"$interactor\","}
          "testcases": [{"seq": 1, "input": "42\n", "expectedOutput": "", "visibility": "HIDDEN"}],
          "templates": []
        }
    """.trimIndent()
}
