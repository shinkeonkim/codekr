package codekr.api.problem.mongo

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.hamcrest.Matchers.containsString
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
 * MongoDB 문제 (#527).
 *
 * **Redis(#455)와 채점 모델이 같고 질의 언어가 다르다.** 그래서 유형과 스펙 표를
 * 나눴다 — #454 가 SQL 에 MariaDB 를 런타임만으로 더할 수 있었던 것과 다른 사정이다.
 */
class MongoProblemIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var redisTemplate: StringRedisTemplate

    private lateinit var adminToken: String
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.SUPERUSER))),
        )
        userToken = tokenProvider.issueAccessToken(
            userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER))),
        )
    }

    @Test
    fun `MongoDB 문제를 만들고 스펙이 그대로 돌아온다`() {
        val id = create(body("mongo-orders"))

        mockMvc.perform(get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(jsonPath("$.problemKind").value("JUDGE_MONGODB"))
            .andExpect(jsonPath("$.mongoSpec.answerScript").value(ANSWER))
            .andExpect(jsonPath("$.mongoSpec.verifyScript").value(VERIFY))
            .andExpect(jsonPath("$.mongoSpec.ignoreOrder").value(false))
    }

    @Test
    fun `테스트케이스가 없어도 공개할 수 있다`() {
        // 채점 대상이 테스트케이스가 아니다 — 확인 스크립트의 출력이다.
        create(body("mongo-publishable", published = true))
    }

    @Test
    fun `확인 스크립트 없이는 만들 수 없다`() {
        // 이것이 없으면 무엇을 정답으로 볼지가 없다.
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("mongo-no-verify").replace("\"verifyScript\": \"$VERIFY\",", "")),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `MongoDB 문제가 아닌데 스펙이 실려 오면 거절한다`() {
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("stdio-with-mongo").replace("\"JUDGE_MONGODB\"", "\"JUDGE_STDIO\"")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("MongoDB 스펙")))
    }

    @Test
    fun `기동 시간을 못 채우는 시간 제한은 거절한다`() {
        // mongod 는 뜨는 데만 수 초가 걸린다. 그것을 빼고 제한을 잡으면 제출이 아무리
        // 빨라도 시간 초과가 나고, 출제자는 자기 문제가 왜 안 되는지 모른다.
        //
        // 하한은 `startupMs + SQL_QUERY_BUDGET_MS` 다. 실측으로 startupMs 를 12초로
        // 올렸으므로(#527) 13초 미만은 거절된다 — 이 시험의 본문이 25초를 쓰는 이유다.
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("mongo-too-tight").replace("\"timeLimitMs\": 25000", "\"timeLimitMs\": 2000")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("뜨는 데만")))
    }

    @Test
    fun `제출이 채점 큐로 스펙을 싣고 간다`() {
        // 채점기가 DB 를 읽지 않는다 (ADR-0004) — 스펙이 메시지에 실려야 한다.
        create(body("mongo-queued", published = true))

        mockMvc.perform(
            post("/api/v1/problems/mongo-queued/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"mongodb:7","sourceCode":"db.orders.find();"}"""),
        ).andExpect(status().isAccepted)

        val queued = redisTemplate.opsForStream<String, String>()
            .range("codekr:judge:normal", Range.unbounded())
            .orEmpty()
            .joinToString(" ") { it.value.values.joinToString(" ") }
        assert(queued.contains("JUDGE_MONGODB")) { "유형이 큐에 안 실렸다" }
        assert(queued.contains("verify")) { "확인 스크립트가 큐에 안 실렸다: ${queued.take(200)}" }
    }

    private fun create(json: String): Long =
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json),
        ).andExpect(status().isCreated)
            .andReturn().response.contentAsString
            .let { Regex("\"id\":(\\d+)").find(it)!!.groupValues[1].toLong() }

    private fun body(slug: String, published: Boolean = false) = """
        {
          "slug": "$slug", "title": "주문 처리",
          "category": "ALGORITHM", "problemKind": "JUDGE_MONGODB", "difficulty": "SILVER_5",
          "description": "주문을 처리하세요.", "published": $published,
          "allowedRuntimeIds": ["mongodb:7"],
          "timeLimitMs": 25000, "memoryLimitMb": 512,
          "mongoSpec": {
            "seedScript": "$SEED",
            "answerScript": "$ANSWER",
            "verifyScript": "$VERIFY",
            "ignoreOrder": false
          },
          "testcases": [], "templates": []
        }
    """.trimIndent()

    private companion object {
        const val SEED = "db.orders.insertOne({ sku: 'A-1', qty: 3, status: 'pending' });"
        const val ANSWER = "db.orders.updateMany({ status: 'pending' }, { \$set: { status: 'shipped' } });"
        const val VERIFY = "db.orders.find({}, { _id: 0 }).sort({ sku: 1 }).forEach(printjson);"
    }
}
