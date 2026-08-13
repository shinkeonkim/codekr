package codekr.api.auth

import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.MessageDigest

/**
 * 비밀번호 재설정 (#315).
 *
 * **누가 부르는지 모른다.** 로그인하지 않은 사람이 아무 주소로나 요청할 수 있으므로,
 * 여기서 확인하는 것의 절반은 "무엇을 하든 밖에서 보이는 결과가 같은가" 다.
 */
class PasswordResetIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var jdbc: JdbcClient

    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(
            User("solver@codekr.dev", passwordEncoder.encode("oldpassword")!!, "풀이왕", setOf(UserRole.USER)),
        )
        userId = user.id
        /*
            **인증된 주소로 만든다** (#233).

            마이그레이션은 그때 있던 행만 채우므로, 새로 만든 계정은 미인증이다.
            재설정은 인증된 주소에만 나가므로 여기서 켜 둔다 — 미인증인 경우는
            아래에 따로 시험이 있다.
        */
        jdbc.sql("UPDATE users SET email_verified_at = now() WHERE id = :id").param("id", userId).update()
    }

    @Test
    fun `가입된 주소로 요청하면 토큰이 생긴다`() {
        request("solver@codekr.dev")
        assertEquals(1, count())
    }

    @Test
    fun `없는 주소로 요청해도 같은 답이 온다`() {
        // 다르게 답하면 **어느 주소가 가입되어 있는지 확인하는 도구**가 된다.
        request("nobody@codekr.dev")
        assertEquals(0, count())
    }

    @Test
    fun `인증하지 않은 주소로는 보내지 않는다`() {
        // 남의 주소를 적고 가입한 계정의 비밀번호를 그 주소 주인이 가져가면 안 된다.
        jdbc.sql("UPDATE users SET email_verified_at = NULL WHERE id = :id").param("id", userId).update()

        request("solver@codekr.dev")
        assertEquals(0, count())
    }

    @Test
    fun `링크로 비밀번호를 바꾼다`() {
        val token = issue()

        mockMvc.perform(
            post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$token","newPassword":"newpassword12"}"""),
        ).andExpect(status().isNoContent)

        // 새 비밀번호로 로그인된다.
        mockMvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"solver@codekr.dev","password":"newpassword12"}"""),
        ).andExpect(status().isOk)

        // 옛 비밀번호는 통하지 않는다.
        mockMvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"solver@codekr.dev","password":"oldpassword"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `앞서 발급된 갱신 토큰은 통하지 않는다`() {
        // **비밀번호를 바꾸는 흔한 이유가 "남이 들어와 있는 것 같아서" 다.**
        // 끊지 않으면 그 사람이 계속 새 토큰을 받아 간다.
        val refresh = login()

        mockMvc.perform(
            post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"${issue()}","newPassword":"newpassword12"}"""),
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$refresh"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `한 번 쓴 링크는 다시 쓰이지 않는다`() {
        val token = issue()
        repeat(2) { attempt ->
            val expected = if (attempt == 0) status().isNoContent else status().isBadRequest
            mockMvc.perform(
                post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"$token","newPassword":"newpassword12"}"""),
            ).andExpect(expected)
        }
    }

    @Test
    fun `만료된 링크는 거절한다`() {
        val token = issue(minutes = -1)
        mockMvc.perform(
            post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$token","newPassword":"newpassword12"}"""),
        ).andExpect(status().isBadRequest)

        assertNull(userRepository.findById(userId).orElseThrow().passwordChangedAt)
    }

    @Test
    fun `짧은 비밀번호는 거절한다`() {
        // 가입과 같은 규칙이다. 한쪽만 고치면 재설정으로 규칙을 우회할 수 있다.
        mockMvc.perform(
            post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"${issue()}","newPassword":"short"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `반복 요청은 멎는다`() {
        request("solver@codekr.dev")
        request("solver@codekr.dev")
        // 발송량이 곧 비용이고 평판이다. 밖에서는 두 번 다 같은 답이지만 한 통만 나간다.
        assertEquals(1, count())
    }

    @Test
    fun `바뀌면 본인에게 알린다`() {
        mockMvc.perform(
            post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"${issue()}","newPassword":"newpassword12"}"""),
        ).andExpect(status().isNoContent)

        // 메일함이 털린 경우에는 메일이 닿지 않을 수 있다 — 웹 알림도 함께 남긴다 (#106).
        val notifications = jdbc.sql("SELECT count(*) FROM notifications WHERE user_id = :id")
            .param("id", userId).query(Int::class.java).single()
        assertEquals(1, notifications)
        assertNotNull(userRepository.findById(userId).orElseThrow().passwordChangedAt)
    }

    private fun request(email: String) {
        mockMvc.perform(
            post("/api/v1/auth/password/reset-requests").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}"""),
        ).andExpect(status().isAccepted)
    }

    private fun login(): String {
        val body = mockMvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"solver@codekr.dev","password":"oldpassword"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return Regex("\"refreshToken\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    private fun count(): Int =
        jdbc.sql("SELECT count(*) FROM password_resets WHERE user_id = :id")
            .param("id", userId).query(Int::class.java).single()

    /** 원본 토큰을 알아야 하므로 직접 넣는다. 서비스는 해시만 저장한다. */
    private fun issue(minutes: Int = 30): String {
        val raw = "reset-token-${System.nanoTime()}"
        val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
        jdbc.sql(
            """
            INSERT INTO password_resets (user_id, token_hash, expires_at)
            VALUES (:userId, :hash, now() + (:minutes * interval '1 minute'))
            """,
        )
            .param("userId", userId).param("hash", hash).param("minutes", minutes)
            .update()
        return raw
    }
}
