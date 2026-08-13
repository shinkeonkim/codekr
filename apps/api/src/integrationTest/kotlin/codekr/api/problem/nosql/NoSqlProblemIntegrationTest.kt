package codekr.api.problem.nosql

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
 * NoSQL 문제 (#455).
 *
 * **채점 모델이 SQL 과 다르다** — 제출이 명령의 연속이고 정답은 끝난 뒤의 상태다.
 * 그래서 스펙도 유형도 따로다.
 */
class NoSqlProblemIntegrationTest : IntegrationTestBase() {

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
    fun `테스트케이스 없이도 NoSQL 문제를 공개할 수 있다`() {
        // 채점 대상은 테스트케이스가 아니라 끝난 뒤의 상태다.
        val id = create()

        mockMvc.perform(get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.problemKind").value("JUDGE_NOSQL"))
            .andExpect(jsonPath("$.nosqlSpec.verifyCommands").value(VERIFY))
            .andExpect(jsonPath("$.nosqlSpec.ignoreOrder").value(false))
    }

    @Test
    fun `상태를 읽는 명령 없이는 낼 수 없다`() {
        /*
          명령의 연속에는 견줄 **결과 집합이 없다.** 확인 명령이 없으면 무엇을 정답으로
          볼지가 없고, 그러면 어떤 제출이든 같은 판정을 받는다.
        */
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("no-verify", verify = null)),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `제품을 고르지 않으면 거부한다`() {
        // 시드와 정답이 그 제품의 명령이다 — 다른 제품으로 제출되면 시드가 먼저 깨진다.
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("no-product", products = emptyList())),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("하나만")))
    }

    @Test
    fun `NoSQL 문제 화면에는 그 제품만 보인다`() {
        create()

        mockMvc.perform(get("/api/v1/problems/nosql-scores"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runtimes.length()").value(1))
            .andExpect(jsonPath("$.runtimes[0].id").value("nosql:redis7"))
    }

    @Test
    fun `제출하면 시드와 확인 명령이 채점 작업에 실린다`() {
        // **채점기는 DB 를 읽지 않는다** (ADR-0004).
        create()

        mockMvc.perform(
            post("/api/v1/problems/nosql-scores/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"nosql:redis7","sourceCode":"ZINCRBY scores 5 kim"}"""),
        ).andExpect(status().isAccepted)

        val queued = redisTemplate.opsForStream<String, String>()
            .range("codekr:judge:normal", Range.unbounded())
            .orEmpty()
            .joinToString(" ") { it.value.values.joinToString(" ") }

        assert(queued.contains(VERIFY)) { "확인 명령이 작업에 없다: $queued" }
        assert(queued.contains("ZADD scores")) { "시드가 작업에 없다: $queued" }
    }

    @Test
    fun `다른 유형에 NoSQL 스펙을 실을 수 없다`() {
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("stdio-with-nosql").replace("\"JUDGE_NOSQL\"", "\"JUDGE_STDIO\"")),
        ).andExpect(status().isBadRequest)
    }

    private fun create(): Long {
        val response = mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("nosql-scores")),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun body(
        slug: String,
        verify: String? = VERIFY,
        products: List<String> = listOf("nosql:redis7"),
    ) = """
        {
          "slug": "$slug", "title": "점수 올리기",
          "category": "ALGORITHM", "problemKind": "JUDGE_NOSQL", "difficulty": "SILVER_5",
          "description": "kim 의 점수를 5 올리세요.", "published": true,
          "timeLimitMs": 5000, "memoryLimitMb": 512,
          "allowedRuntimeIds": [${products.joinToString { "\"$it\"" }}],
          "testcases": [], "templates": [],
          "nosqlSpec": {
            "seedCommands": "ZADD scores 10 kim",
            "answerCommands": "ZINCRBY scores 5 kim",
            ${if (verify == null) "" else "\"verifyCommands\": \"$verify\","}
            "ignoreOrder": false
          }
        }
    """.trimIndent()

    private companion object {
        const val VERIFY = "ZRANGE scores 0 -1 WITHSCORES"
    }
}
