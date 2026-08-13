package codekr.api.problem.sql

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
 * 상태를 바꾸는 SQL 문제 (#453).
 *
 * `SELECT` 문제는 **결과 집합**을 견주면 됐다. `INSERT`·`UPDATE`·`CREATE TABLE` 은
 * 결과 집합이 없다 — 바뀌는 것은 **DB 의 상태**다.
 */
class SqlWriteProblemIntegrationTest : IntegrationTestBase() {

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
    fun `쓰기를 열면 끝난 뒤의 상태를 읽는 쿼리가 있어야 한다`() {
        /*
          없으면 채점이 **조용히** 결과 집합 비교로 돌아간다. `UPDATE` 의 결과 집합은
          비어 있으니 아무 답이나 통과하고, 출제자는 자기 문제가 무엇을 재는지 모른다.
        */
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("write-no-verify", verify = null, allowWrite = true)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(
                jsonPath("$.message").value(org.hamcrest.Matchers.containsString("상태를 읽는 쿼리")),
            )
    }

    @Test
    fun `검사 쿼리와 쓰기 여부가 저장된다`() {
        val id = create("write-city")

        mockMvc.perform(get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sqlSpec.verifySql").value(VERIFY))
            .andExpect(jsonPath("$.sqlSpec.allowWrite").value(true))
    }

    @Test
    fun `제출하면 검사 쿼리와 쓰기 신호가 채점 작업에 실린다`() {
        // **채점기는 DB 를 읽지 않는다** (ADR-0004). 실리지 않으면 하네스가 데이터베이스를
        // 하나만 만들고, 무엇을 견줄지 알 방법이 없다.
        create("write-city")

        mockMvc.perform(
            post("/api/v1/problems/write-city/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"sql:postgres16","sourceCode":"UPDATE members SET city='서울';"}"""),
        ).andExpect(status().isAccepted)

        val queued = queuedJob()
        assert(queued.contains(VERIFY)) { "검사 쿼리가 작업에 없다: $queued" }
        assert(queued.contains("\"allowWrite\":true")) { "쓰기 신호가 작업에 없다: $queued" }
    }

    @Test
    fun `쓰기를 열지 않은 문제는 지금과 같다`() {
        // 지금 있는 SELECT 문제가 조용히 쓰기 가능해지면 안 된다.
        val id = create("read-city", verify = null, allowWrite = false)

        mockMvc.perform(get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(jsonPath("$.sqlSpec.allowWrite").value(false))
            .andExpect(jsonPath("$.sqlSpec.verifySql").doesNotExist())
    }

    @Test
    fun `검사 쿼리만 두고 읽기 전용으로 낼 수 있다`() {
        /*
          둘은 다른 결정이다. 결과 집합이 아니라 상태를 보는 문제인데 제출은 읽기만
          하는 경우 — 예를 들어 트랜잭션이 끝난 뒤의 상태를 묻는 문제 — 가 있다.
        */
        val id = create("verify-only", verify = VERIFY, allowWrite = false)

        mockMvc.perform(get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sqlSpec.verifySql").value(VERIFY))
            .andExpect(jsonPath("$.sqlSpec.allowWrite").value(false))
    }

    private fun queuedJob(): String =
        redisTemplate.opsForStream<String, String>()
            .range("codekr:judge:normal", Range.unbounded())
            .orEmpty()
            .joinToString(" ") { it.value.values.joinToString(" ") }

    private fun create(
        slug: String,
        verify: String? = VERIFY,
        allowWrite: Boolean = true,
    ): Long {
        val response = mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(slug, verify, allowWrite)),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun body(slug: String, verify: String?, allowWrite: Boolean) = """
        {
          "slug": "$slug", "title": "부산 사람을 서울로 옮기기",
          "category": "SQL", "problemKind": "JUDGE_SQL", "difficulty": "SILVER_5",
          "description": "부산에 사는 사람의 도시를 서울로 바꾸세요.", "published": true,
          "allowedRuntimeIds": ["sql:postgres16"],
          "testcases": [], "templates": [],
          "sqlSpec": {
            "schemaSql": "CREATE TABLE members (id int, city text);",
            "answerSql": "UPDATE members SET city='서울' WHERE city='부산';",
            "ignoreRowOrder": true,
            ${if (verify == null) "" else "\"verifySql\": \"$verify\","}
            "allowWrite": $allowWrite
          }
        }
    """.trimIndent()

    private companion object {
        const val VERIFY = "SELECT id, city FROM members ORDER BY id;"
    }
}
