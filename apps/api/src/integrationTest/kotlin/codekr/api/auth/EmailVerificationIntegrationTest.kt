package codekr.api.auth

import codekr.api.auth.email.EmailVerificationRepository
import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.MessageDigest

/**
 * 이메일 인증 (#233).
 *
 * **주소가 진짜인지 확인하지 않았다.** 비밀번호를 잃으면 계정을 잃는 것과 같고(#315),
 * 소속 인증(#240)은 도메인 확인에 기대는데 확인되지 않은 주소로는 뜻이 없다.
 *
 * 토큰은 표에 해시로만 남으므로, 여기서는 **발급된 원본 토큰을 알 수 없다.**
 * 그래서 서비스가 만든 해시로 거꾸로 맞춰 보는 대신, 토큰을 직접 만들어 넣고 확인한다.
 */
class EmailVerificationIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var verifications: EmailVerificationRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbc: JdbcClient

    private lateinit var token: String
    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER)))
        userId = user.id
        token = tokenProvider.issueAccessToken(user)
        // 마이그레이션이 기존 계정을 인증된 것으로 채우므로, 새로 만든 계정도 그렇게 온다.
        // 이 시험은 **아직 확인하지 않은 상태**를 다루므로 비워 둔다.
        jdbc.sql("UPDATE users SET email_verified_at = NULL WHERE id = :id").param("id", userId).update()
    }

    @Test
    fun `가입하면 토큰이 발급된다`() {
        mockMvc.perform(
            post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(signupBody("new@codekr.dev", "password1234", "새사람")),
        ).andExpect(status().isCreated)

        val created = userRepository.findByEmail("new@codekr.dev")!!
        assertNotNull(verifications.findFirstByUserIdAndEmailOrderByIdDesc(created.id, null))
        // **가입은 성공한다.** 메일 설정이 없어도(로컬이 그렇다) 발급까지는 된다.
        assertNull(created.emailVerifiedAt)
    }

    @Test
    fun `링크를 누르면 확인된다`() {
        val raw = issue()

        mockMvc.perform(
            post("/api/v1/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$raw"}"""),
        ).andExpect(status().isNoContent)

        assertNotNull(userRepository.findById(userId).orElseThrow().emailVerifiedAt)
    }

    @Test
    fun `로그인 없이도 확인할 수 있다`() {
        // 메일을 받은 기기와 로그인한 기기가 다를 수 있다. 토큰 자체가 본인 확인이다.
        val raw = issue()
        mockMvc.perform(
            post("/api/v1/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$raw"}"""),
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `없는 토큰은 거절한다`() {
        mockMvc.perform(
            post("/api/v1/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"아무거나"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `만료된 토큰은 거절한다`() {
        val raw = issue(expiresInHours = -1)
        mockMvc.perform(
            post("/api/v1/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$raw"}"""),
        ).andExpect(status().isBadRequest)

        assertNull(userRepository.findById(userId).orElseThrow().emailVerifiedAt)
    }

    @Test
    fun `같은 링크를 두 번 눌러도 오류가 아니다`() {
        // 사용자가 링크를 두 번 누른 것뿐이다. 이미 확인된 계정이면 조용히 성공이다.
        val raw = issue()
        repeat(2) {
            mockMvc.perform(
                post("/api/v1/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"$raw"}"""),
            ).andExpect(status().isNoContent)
        }
    }

    @Test
    fun `확인 여부가 내 정보에 실린다`() {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.emailVerified").value(false))

        mockMvc.perform(
            post("/api/v1/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"${issue()}"}"""),
        ).andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.emailVerified").value(true))
    }

    @Test
    fun `재발송은 쿨다운에 걸린다`() {
        mockMvc.perform(post("/api/v1/auth/email/verification").header("Authorization", "Bearer $token"))
            .andExpect(status().isAccepted)
        // 발송량이 곧 비용이고 평판이다.
        mockMvc.perform(post("/api/v1/auth/email/verification").header("Authorization", "Bearer $token"))
            .andExpect(status().isTooManyRequests)
    }

    @Test
    fun `확인이 끝난 계정은 다시 보내지 않는다`() {
        jdbc.sql("UPDATE users SET email_verified_at = now() WHERE id = :id").param("id", userId).update()

        mockMvc.perform(post("/api/v1/auth/email/verification").header("Authorization", "Bearer $token"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `메일을 보낼 수 없으면 글쓰기를 막지 않는다`() {
        // **요구는 보낼 수 있을 때만 뜻이 있다** — 보내지도 못하면서 막으면 아무도
        // 인증할 수 없는 계정이 만들어진다. 시험 환경에는 메일 설정이 없다.
        mockMvc.perform(
            post("/api/v1/posts").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"board":"FREE","title":"제목","body":"내용"}"""),
        ).andExpect(status().isCreated)
    }

    /** 원본 토큰을 알아야 하므로 직접 넣는다. 서비스는 해시만 저장한다. */
    private fun issue(expiresInHours: Long = 24): String {
        val raw = "test-token-${System.nanoTime()}"
        val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
        jdbc.sql(
            """
            INSERT INTO email_verifications (user_id, token_hash, expires_at)
            VALUES (:userId, :hash, now() + (:hours * interval '1 hour'))
            """,
        )
            .param("userId", userId)
            .param("hash", hash)
            .param("hours", expiresInHours.toInt())
            .update()
        return raw
    }
}
