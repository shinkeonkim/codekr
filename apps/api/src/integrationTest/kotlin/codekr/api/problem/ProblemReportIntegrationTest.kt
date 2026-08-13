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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 문제 오류 신고 (#478).
 *
 * **질문(#139)과 다른 것이다** — 안 보고 넘기면 그 사람만 아쉬운 것이 아니라 모든
 * 제출이 계속 잘못 채점된다. 그리고 **받기만 하면 한 번 하고 마는 일**이 되므로,
 * 받아들여진 신고는 문제 페이지에 이름을 남긴다.
 */
class ProblemReportIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private lateinit var adminToken: String
    private lateinit var userToken: String
    private var userId: Long = 0
    private var problemId: Long = 0

    @BeforeEach
    fun setUp() {
        val admin = userRepository.save(
            User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.SUPERUSER)),
        )
        adminToken = tokenProvider.issueAccessToken(admin)
        val user = userRepository.save(User("reporter@codekr.dev", "x", "신고자", setOf(UserRole.USER)))
        userId = user.id
        userToken = tokenProvider.issueAccessToken(user)
        problemId = jdbcClient.sql(
            """
            INSERT INTO problems (slug, title, category, description, published,
                                  difficulty_level, difficulty_state)
            VALUES ('broken', '고장난 문제', 'ALGORITHM', '설명', true, 5, 'RATED') RETURNING id
            """,
        ).query(Long::class.java).single()
    }

    @Test
    fun `문제가 잘못됐다고 알릴 수 있다`() {
        report("MISSING_TESTCASE", "N=1 인 경우가 없습니다.")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.kindLabel").value("테스트케이스 부족"))
            .andExpect(jsonPath("$.status").value("OPEN"))
    }

    @Test
    fun `한 사람이 같은 문제에 열린 신고를 둘 두지 않는다`() {
        // 열 명이 같은 것을 말하는 것은 정보지만, 한 사람이 열 번 말하는 것은 목록만 흐린다.
        report("MISSING_TESTCASE", "첫 신고").andExpect(status().isCreated)

        report("WRONG_STATEMENT", "두 번째 신고").andExpect(status().isBadRequest)
    }

    @Test
    fun `어드민 목록에서 열린 신고부터 본다`() {
        report("MISSING_CONSTRAINT", "N 의 범위가 없습니다.").andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/v1/admin/problem-reports").param("status", "OPEN")
                .header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].openCount").value(1))
    }

    @Test
    fun `받아들이면 신고자가 문제의 기여자로 남는다`() {
        /*
          **받기만 하면 한 번 하고 마는 일이 된다.** 문제 페이지에 이름이 남는 것이
          계속할 이유가 된다 (#236 의 자리).
        */
        val id = reportId("MISSING_TESTCASE", "N=1 이 없습니다.")

        resolve(id, "ACCEPTED", "테스트케이스를 더했습니다.").andExpect(status().isOk)

        val roles = jdbcClient.sql("SELECT role FROM problem_credits WHERE problem_id = :p AND user_id = :u")
            .param("p", problemId).param("u", userId).query(String::class.java).list()
        assert(roles == listOf("CONTRIBUTOR")) { "기여자로 남아야 합니다: $roles" }
    }

    @Test
    fun `문제를 저장해도 기여자는 지워지지 않는다`() {
        /*
          출제·검수는 어드민이 고르는 값이라 저장할 때 통째로 갈아 끼운다. 기여자를 그
          목록과 함께 지우면 **고친 사람의 이름이 다음 편집에서 사라진다.**
        */
        val id = reportId("WRONG_ANSWER", "예제 2의 답이 틀렸습니다.")
        resolve(id, "ACCEPTED", "고쳤습니다.").andExpect(status().isOk)

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/admin/problems/$problemId")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "slug": "broken", "title": "고장난 문제", "category": "ALGORITHM",
                      "description": "설명", "published": true,
                      "testcases": [{"seq": 1, "input": "1", "expectedOutput": "1", "visibility": "PUBLIC"}],
                      "templates": []
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isOk)

        val roles = jdbcClient.sql("SELECT role FROM problem_credits WHERE problem_id = :p AND user_id = :u")
            .param("p", problemId).param("u", userId).query(String::class.java).list()
        assert(roles == listOf("CONTRIBUTOR")) { "편집해도 기여자는 남아야 합니다: $roles" }
    }

    @Test
    fun `이유 없이 거절할 수 없다`() {
        // 이유 없는 거절은 신고한 사람에게 "읽지 않았다" 와 구분되지 않는다.
        val id = reportId("OTHER", "뭔가 이상합니다.")

        resolve(id, "REJECTED", null).andExpect(status().isBadRequest)
    }

    @Test
    fun `처리하면 신고자에게 알린다`() {
        val id = reportId("MISSING_TESTCASE", "N=1 이 없습니다.")

        resolve(id, "ACCEPTED", "테스트케이스를 더했습니다.").andExpect(status().isOk)

        val notified = jdbcClient.sql(
            "SELECT count(*) FROM notifications WHERE user_id = :u AND body LIKE '%테스트케이스를 더했습니다%'",
        ).param("u", userId).query(Int::class.java).single()
        assert(notified == 1) { "말없이 닫으면 읽지 않은 것과 구분되지 않는다: ${notified}건" }
    }

    @Test
    fun `한 번 처리한 신고는 다시 처리하지 않는다`() {
        // 두 번 처리되면 알림이 두 번 가고, 어느 것이 결론인지 알 수 없다.
        val id = reportId("OTHER", "설명이 애매합니다.")
        resolve(id, "REJECTED", "의도한 것입니다.").andExpect(status().isOk)

        resolve(id, "ACCEPTED", "역시 고칩니다.").andExpect(status().isBadRequest)
    }

    private fun report(kind: String, body: String) = mockMvc.perform(
        post("/api/v1/problems/broken/reports")
            .header("Authorization", "Bearer $userToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"kind":"$kind","body":"$body"}"""),
    )

    private fun reportId(kind: String, body: String): Long =
        report(kind, body).andExpect(status().isCreated).andReturn().response.contentAsString
            .let { Regex("\"id\":(\\d+)").find(it)!!.groupValues[1].toLong() }

    private fun resolve(id: Long, status: String, resolution: String?) = mockMvc.perform(
        post("/api/v1/admin/problem-reports/$id/resolution")
            .header("Authorization", "Bearer $adminToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                if (resolution == null) """{"status":"$status"}"""
                else """{"status":"$status","resolution":"$resolution"}""",
            ),
    )
}
