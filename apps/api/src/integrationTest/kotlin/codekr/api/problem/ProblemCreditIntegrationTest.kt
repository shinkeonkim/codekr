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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 문제의 출제자·검수자·출처 (#236).
 *
 * **문제가 어디서 왔고 누가 만들었는지 남지 않았다.** 잘못된 문제를 누구에게 물어야
 * 하는지 몰랐고, 출처를 적을 칸이 없으니 안 적는 것이 기본이었다.
 */
class ProblemCreditIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbc: JdbcClient

    private lateinit var adminToken: String
    private var setterId: Long = 0
    private var reviewerId: Long = 0

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.SUPERUSER))),
        )
        setterId = userRepository.save(User("setter@codekr.dev", "x", "출제자", setOf(UserRole.PROBLEM_SETTER))).id
        reviewerId = userRepository.save(User("reviewer@codekr.dev", "x", "검수자", setOf(UserRole.USER))).id
    }

    @Test
    fun `출제자와 검수자와 출처가 문제에 남는다`() {
        create(setters = listOf(setterId), reviewers = listOf(reviewerId))

        mockMvc.perform(get("/api/v1/problems/credited"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.setters[0].nickname").value("출제자"))
            .andExpect(jsonPath("$.setters[0].roleLabel").value("출제"))
            .andExpect(jsonPath("$.reviewers[0].nickname").value("검수자"))
            .andExpect(jsonPath("$.sourceLabel").value("직접 만든 문제"))
            .andExpect(jsonPath("$.sourceUrl").value("https://codekr.dev/about"))
    }

    @Test
    fun `여러 명을 붙일 수 있다`() {
        // 문제 하나를 둘이 만드는 일은 흔하고, 검수는 더 그렇다 — 처음부터 다대다다.
        create(setters = listOf(setterId, reviewerId), reviewers = emptyList())

        mockMvc.perform(get("/api/v1/problems/credited"))
            .andExpect(jsonPath("$.setters.length()").value(2))
    }

    @Test
    fun `다시 저장하면 통째로 갈아 끼운다`() {
        val id = create(setters = listOf(setterId), reviewers = listOf(reviewerId))

        update(id, setters = listOf(reviewerId), reviewers = emptyList())

        mockMvc.perform(get("/api/v1/problems/credited"))
            .andExpect(jsonPath("$.setters.length()").value(1))
            .andExpect(jsonPath("$.setters[0].nickname").value("검수자"))
            // 뺀 사람이 남아 있으면 안 된다.
            .andExpect(jsonPath("$.reviewers.length()").value(0))
    }

    @Test
    fun `없는 회원을 지정하면 막힌다`() {
        mockMvc.perform(
            post("/api/v1/admin/problems").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(slug = "nobody", setters = listOf(999999), reviewers = emptyList())),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `탈퇴한 출제자는 탈퇴한 사용자로 보인다`() {
        // 이름을 지우지 않는 이유는 그 사람이 만든 사실 자체는 남는 것이기 때문이다 (#140).
        create(setters = listOf(setterId), reviewers = emptyList())
        jdbc.sql("UPDATE users SET withdrawn_at = now(), nickname = '탈퇴회원' || id WHERE id = :id")
            .param("id", setterId).update()

        mockMvc.perform(get("/api/v1/problems/credited"))
            .andExpect(jsonPath("$.setters[0].nickname").value("탈퇴한 사용자"))
    }

    @Test
    fun `출처는 없어도 된다`() {
        // 필수로 두면 자체 제작 문제에 "자체 제작" 을 매번 적게 된다.
        mockMvc.perform(
            post("/api/v1/admin/problems").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(slug = "no-source", setters = emptyList(), reviewers = emptyList(), source = false)),
        ).andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/problems/no-source"))
            .andExpect(jsonPath("$.sourceLabel").doesNotExist())
    }

    private fun create(setters: List<Long>, reviewers: List<Long>): Long {
        val response = mockMvc.perform(
            post("/api/v1/admin/problems").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("credited", setters, reviewers)),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun update(id: Long, setters: List<Long>, reviewers: List<Long>) {
        mockMvc.perform(
            put("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("credited", setters, reviewers)),
        ).andExpect(status().isOk)
    }

    private fun body(
        slug: String,
        setters: List<Long>,
        reviewers: List<Long>,
        source: Boolean = true,
    ): String {
        val sourceJson = if (source) {
            ""","sourceLabel":"직접 만든 문제","sourceUrl":"https://codekr.dev/about""""
        } else {
            ""
        }
        return """
            {
              "slug": "$slug", "title": "이름 붙은 문제", "category": "ALGORITHM",
              "description": "지문", "timeLimitMs": 2000, "memoryLimitMb": 256,
              "published": true,
              "setterIds": ${setters.joinToString(prefix = "[", postfix = "]")},
              "reviewerIds": ${reviewers.joinToString(prefix = "[", postfix = "]")}$sourceJson,
              "testcases": [{"seq":1,"input":"1\n","expectedOutput":"1\n","visibility":"PUBLIC"}]
            }
        """.trimIndent()
    }
}
