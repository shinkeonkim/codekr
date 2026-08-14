package codekr.api.contest

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 참가 승인 (#466).
 *
 * 전에는 **누르면 그 순간 참가자**였다. "등록했다" 와 "참가자다" 사이가 없어서
 * 자격을 확인하고 받는 대회를 낼 수 없었다.
 *
 * **공개 범위(#465)와 직교한다** — 이 시험은 공개 대회에 승인만 걸어 본다.
 */
class ContestApprovalIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private var adminToken: String = ""
    private var userToken: String = ""
    private var userId: Long = 0
    private var contestId: Long = 0

    @BeforeEach
    fun setUp() {
        val admin = userRepository.save(
            User("manager@codekr.dev", "x", "운영자", setOf(UserRole.USER, UserRole.CONTEST_MANAGER)),
        )
        adminToken = tokenProvider.issueAccessToken(admin)
        val user = userRepository.save(User("solver@codekr.dev", "x", "신청자", setOf(UserRole.USER)))
        userId = user.id
        userToken = tokenProvider.issueAccessToken(user)
        contestId = contest(requiresApproval = true)
    }

    @Test
    fun `승인이 필요한 대회는 신청까지만 된다`() {
        register()

        mockMvc.perform(get("/api/v1/contests/approval-contest").header("Authorization", "Bearer $userToken"))
            .andExpect(jsonPath("$.registered").value(false))
            .andExpect(jsonPath("$.pendingApproval").value(true))
    }

    @Test
    fun `승인 전에는 제출할 수 없다`() {
        /*
          **화면이 아니라 서버가 막는다.** API 를 직접 부르면 통과하는 상태를 두면
          승인이 아무 뜻도 없다.
        */
        register()
        // 제출은 대회가 도는 동안만 받는다 — 그 문을 지난 뒤에 승인이 막는지 본다.
        jdbcClient.sql("UPDATE contests SET starts_at = now() - interval '10 minute' WHERE id = :id")
            .param("id", contestId).update()

        mockMvc.perform(
            post("/api/v1/contests/approval-contest/problems/p-1/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.13","sourceCode":"print(3)"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("참가 승인을 기다리는 중입니다."))
    }

    @Test
    fun `승인하면 참가자가 된다`() {
        register()

        mockMvc.perform(
            post("/api/v1/admin/contests/$contestId/applicants/$userId/approval")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/contests/approval-contest").header("Authorization", "Bearer $userToken"))
            .andExpect(jsonPath("$.registered").value(true))
            .andExpect(jsonPath("$.pendingApproval").value(false))
    }

    @Test
    fun `대기 목록에 신청자가 보인다`() {
        register()

        mockMvc.perform(
            get("/api/v1/admin/contests/$contestId/applicants").header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].nickname").value("신청자"))
    }

    @Test
    fun `거절하면 행이 지워지고 사유가 전해진다`() {
        // 상태로 남기면 다시 신청할 수 없다. "왜" 는 관리 기록(#225)이 답한다.
        register()

        mockMvc.perform(
            delete("/api/v1/admin/contests/$contestId/applicants/$userId")
                .param("reason", "수강생이 아닙니다")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isNoContent)

        val rows = jdbcClient.sql(
            "SELECT count(*) FROM contest_registrations WHERE contest_id = :c AND user_id = :u",
        ).param("c", contestId).param("u", userId).query(Int::class.java).single()
        assert(rows == 0) { "거절은 행을 지운다 — 그래야 다시 신청할 수 있다" }

        val notified = jdbcClient.sql(
            "SELECT count(*) FROM notifications WHERE user_id = :u AND body LIKE '%수강생이 아닙니다%'",
        ).param("u", userId).query(Int::class.java).single()
        assert(notified == 1) { "말없이 사라지면 다시 신청할지도 모른다: ${notified}건" }
    }

    @Test
    fun `사유 없이 거절할 수 없다`() {
        register()

        mockMvc.perform(
            delete("/api/v1/admin/contests/$contestId/applicants/$userId")
                .param("reason", " ")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `시작한 뒤에는 승인할 수 없다`() {
        /*
          **늦게 승인된 사람은 남들보다 시간이 적다.** 그것을 열어 두면 순위표가 무엇을
          재는지 흐려진다.
        */
        register()
        jdbcClient.sql("UPDATE contests SET starts_at = now() - interval '10 minute' WHERE id = :id")
            .param("id", contestId).update()

        mockMvc.perform(
            post("/api/v1/admin/contests/$contestId/applicants/$userId/approval")
                .header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("시작한 뒤에는")))
    }

    @Test
    fun `승인을 쓰지 않는 대회는 지금과 같다`() {
        // 기존 대회가 그대로 돌아야 한다 — 누르면 그 순간 참가자다.
        val open = contest(requiresApproval = false, slug = "open-contest")

        mockMvc.perform(
            post("/api/v1/contests/open-contest/registrations").header("Authorization", "Bearer $userToken"),
        ).andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/contests/open-contest").header("Authorization", "Bearer $userToken"))
            .andExpect(jsonPath("$.registered").value(true))
            .andExpect(jsonPath("$.pendingApproval").value(false))
        assert(open > 0)
    }

    private fun register() {
        mockMvc.perform(
            post("/api/v1/contests/approval-contest/registrations")
                .header("Authorization", "Bearer $userToken"),
        ).andExpect(status().isNoContent)
    }

    private fun contest(requiresApproval: Boolean, slug: String = "approval-contest"): Long =
        jdbcClient.sql(
            """
            INSERT INTO contests (slug, title, description, starts_at, ends_at, status,
                                  requires_approval, created_by)
            VALUES (:slug, :slug, '', now() + interval '1 hour', now() + interval '3 hour',
                    'PUBLISHED', :approval, :u)
            RETURNING id
            """,
        )
            .param("slug", slug)
            .param("approval", requiresApproval)
            .param("u", userId)
            .query(Long::class.java)
            .single()
}
