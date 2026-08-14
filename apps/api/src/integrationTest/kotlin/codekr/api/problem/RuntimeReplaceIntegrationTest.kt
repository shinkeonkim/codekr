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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 허용 런타임·하네스를 다시 저장할 때 (#560).
 *
 * **지웠다 다시 넣으면 유니크 제약에 걸린다.** Hibernate 가 한 flush 안에서 INSERT 를
 * DELETE 보다 먼저 내보내기 때문이다. 아희 문제를 공개하려다 운영에서 터졌다 —
 * 공개가 안 되는 것이 아니라 **저장 자체가 안 됐다.**
 */
class RuntimeReplaceIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private var adminToken: String = ""

    @BeforeEach
    fun setUp() {
        val admin = userRepository.save(
            User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.SUPERUSER)),
        )
        adminToken = tokenProvider.issueAccessToken(admin)
    }

    @Test
    fun `같은 런타임을 유지한 채 다시 저장할 수 있다`() {
        // 운영에서 난 것이 정확히 이 경우다 — 아희 문제를 공개하려고 저장했다.
        val id = create(listOf("aheui:1.2"))

        update(id, listOf("aheui:1.2"), published = true).andExpect(status().isOk)

        assert(runtimeIdsOf(id) == listOf("aheui:1.2")) { "허용 목록이 어긋났다: ${runtimeIdsOf(id)}" }
    }

    @Test
    fun `여러 번 저장해도 행이 늘지 않는다`() {
        val id = create(listOf("python:3.13", "cpp:17"))

        repeat(3) { update(id, listOf("python:3.13", "cpp:17")).andExpect(status().isOk) }

        assert(runtimeIdsOf(id) == listOf("cpp:17", "python:3.13")) {
            "행이 늘거나 사라졌다: ${runtimeIdsOf(id)}"
        }
    }

    @Test
    fun `빼면 빠지고 더하면 더해진다`() {
        val id = create(listOf("python:3.13", "cpp:17"))

        // python 은 그대로 두고, cpp 를 빼고 java 를 넣는다.
        update(id, listOf("python:3.13", "java:21")).andExpect(status().isOk)

        assert(runtimeIdsOf(id) == listOf("java:21", "python:3.13")) {
            "차이가 반영되지 않았다: ${runtimeIdsOf(id)}"
        }
    }

    @Test
    fun `전부 비우면 전부 빠진다`() {
        val id = create(listOf("python:3.13"))

        update(id, emptyList()).andExpect(status().isOk)

        assert(runtimeIdsOf(id).isEmpty()) { "남아 있다: ${runtimeIdsOf(id)}" }
    }

    @Test
    fun `하네스도 같은 런타임으로 다시 저장할 수 있다`() {
        // 아직 운영에서 안 터졌을 뿐 같은 결함이었다 (#446).
        val id = createFunction("""{"python:3.13": "from solution import solve"}""")

        update(id, emptyList(), harnesses = """{"python:3.13": "SECRET = 1"}""", kind = "JUDGE_FUNCTION")
            .andExpect(status().isOk)

        val sources = jdbcClient.sql("SELECT source FROM problem_harnesses WHERE problem_id = :id")
            .param("id", id).query(String::class.java).list().filterNotNull()
        assert(sources == listOf("SECRET = 1")) { "하네스 내용이 안 바뀌었다: $sources" }
    }

    private fun runtimeIdsOf(id: Long): List<String> =
        jdbcClient.sql("SELECT runtime_id FROM problem_allowed_runtimes WHERE problem_id = :id ORDER BY runtime_id")
            .param("id", id).query(String::class.java).list().filterNotNull()

    private fun create(runtimeIds: List<String>): Long =
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("runtime-replace", runtimeIds)),
        ).andExpect(status().isCreated)
            .andReturn().response.contentAsString
            .let { Regex("\"id\":(\\d+)").find(it)!!.groupValues[1].toLong() }

    private fun createFunction(harnesses: String): Long =
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("harness-replace", emptyList(), harnesses, "JUDGE_FUNCTION")),
        ).andExpect(status().isCreated)
            .andReturn().response.contentAsString
            .let { Regex("\"id\":(\\d+)").find(it)!!.groupValues[1].toLong() }

    private fun update(
        id: Long,
        runtimeIds: List<String>,
        harnesses: String = "{}",
        kind: String = "JUDGE_STDIO",
        published: Boolean = false,
    ) = mockMvc.perform(
        put("/api/v1/admin/problems/$id")
            .header("Authorization", "Bearer $adminToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(if (kind == "JUDGE_FUNCTION") "harness-replace" else "runtime-replace", runtimeIds, harnesses, kind, published)),
    )

    private fun body(
        slug: String,
        runtimeIds: List<String>,
        harnesses: String = "{}",
        kind: String = "JUDGE_STDIO",
        published: Boolean = false,
    ) = """
        {
          "slug": "$slug", "title": "다시 저장",
          "category": "ALGORITHM", "difficulty": "BRONZE_5",
          "problemKind": "$kind",
          "description": "저장을 두 번 한다.", "published": $published,
          "allowedRuntimeIds": [${runtimeIds.joinToString { "\"$it\"" }}],
          "harnesses": $harnesses,
          "testcases": [{"seq": 1, "input": "1\n", "expectedOutput": "1\n"}],
          "templates": []
        }
    """.trimIndent()
}
