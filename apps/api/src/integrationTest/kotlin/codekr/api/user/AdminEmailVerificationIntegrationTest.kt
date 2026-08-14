package codekr.api.user

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
 * 어드민이 인증에 손대는 두 가지 (#524).
 *
 * **메일이 안 가면 그 사람은 글도 댓글도 못 쓴다** (#233). 지금까지 그때 할 수 있는 것이
 * DB 를 직접 고치는 것뿐이었다.
 */
class AdminEmailVerificationIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private lateinit var adminToken: String
    private lateinit var superToken: String
    private var memberId: Long = 0

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.ADMIN))),
        )
        superToken = tokenProvider.issueAccessToken(
            userRepository.save(User("super@codekr.dev", "x", "최고관리자", setOf(UserRole.USER, UserRole.SUPERUSER))),
        )
        memberId = userRepository.save(User("member@codekr.dev", "x", "가입자", setOf(UserRole.USER))).id
    }

    @Test
    fun `어드민 상세에 인증 여부가 보인다`() {
        // 상세를 열어도 알 수 없으면, "왜 글을 못 쓰나" 를 물어보기 전에는 아무도 모른다.
        mockMvc.perform(get("/api/v1/admin/users/$memberId").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.emailVerifiedAt").doesNotExist())
    }

    @Test
    fun `어드민 목록에도 인증 여부가 보인다`() {
        mockMvc.perform(
            get("/api/v1/admin/users").param("query", "member")
                .header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].emailVerifiedAt").doesNotExist())
    }

    @Test
    fun `인증 메일을 다시 보낼 수 있다`() {
        // 로컬·시험에는 메일 설정이 없다. **그 사실을 감추지 않고 그대로 말한다** —
        // "보냈다" 고 해 놓고 아무것도 안 가면 어드민은 될 때까지 다시 누른다.
        resend(adminToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mail").value("SKIPPED"))
            .andExpect(jsonPath("$.emailVerifiedAt").doesNotExist())

        val tokens = jdbcClient.sql("SELECT count(*) FROM email_verifications WHERE user_id = :u")
            .param("u", memberId).query(Int::class.java).single()
        assert(tokens == 1) { "토큰이 하나 생겨야 합니다: ${tokens}개" }
    }

    @Test
    fun `재발송은 사용자 한도에 걸리지 않는다`() {
        /*
          사용자는 60초에 한 번·하루 5통이다 (#233). **어드민은 그 한도 때문에 막힌
          사람을 돕는 자리**라 같은 한도를 걸면 이 기능의 목적이 무너진다.
        */
        repeat(6) { resend(adminToken).andExpect(status().isOk) }

        val tokens = jdbcClient.sql("SELECT count(*) FROM email_verifications WHERE user_id = :u")
            .param("u", memberId).query(Int::class.java).single()
        assert(tokens == 6) { "여섯 번 다 나가야 합니다: ${tokens}개" }
    }

    @Test
    fun `강제 인증은 SUPERUSER 만 할 수 있다`() {
        // **확인을 건너뛰는 일**이다 (#233 이 인증을 넣은 이유를 무르는 것이다).
        forceVerify(adminToken, "메일이 계속 반송됩니다").andExpect(status().isForbidden)

        forceVerify(superToken, "메일이 계속 반송됩니다")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.emailVerifiedAt").exists())
    }

    @Test
    fun `사유 없이 강제 인증할 수 없다`() {
        // 나중에 그 계정의 주소를 믿을 수 있는지 물으면, 이 사유가 유일한 답이다.
        forceVerify(superToken, null).andExpect(status().isBadRequest)
    }

    @Test
    fun `누가 왜 강제 인증했는지 기록에 남는다`() {
        forceVerify(superToken, "메일이 계속 반송됩니다").andExpect(status().isOk)

        val reason = jdbcClient.sql(
            "SELECT reason FROM admin_audit_logs WHERE action = 'EMAIL_VERIFY_FORCED' AND target_id = :t",
        ).param("t", memberId).query(String::class.java).single()
        assert(reason == "메일이 계속 반송됩니다") { "사유가 남아야 합니다: $reason" }
    }

    @Test
    fun `이미 인증한 사람에게는 둘 다 하지 않는다`() {
        // 두 번 하면 기록만 늘고 바뀌는 것이 없다. 인증 시각이 덮이지도 않는다.
        forceVerify(superToken, "메일이 계속 반송됩니다").andExpect(status().isOk)

        forceVerify(superToken, "또").andExpect(status().isBadRequest)
        resend(adminToken).andExpect(status().isBadRequest)
    }

    private fun resend(token: String) = mockMvc.perform(
        post("/api/v1/admin/users/$memberId/email-verification/resend")
            .header("Authorization", "Bearer $token"),
    )

    private fun forceVerify(token: String, reason: String?) = mockMvc.perform(
        post("/api/v1/admin/users/$memberId/email-verification")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(if (reason == null) "{}" else """{"reason":"$reason"}"""),
    )
}
