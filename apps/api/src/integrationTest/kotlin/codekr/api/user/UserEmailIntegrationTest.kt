package codekr.api.user

import codekr.api.auth.email.EmailVerificationRepository
import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.email.repository.UserEmailRepository
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 확인한 메일 주소 여러 개 (#396, #240 1단계).
 *
 * **소속 인증이 여기에 통째로 기댄다.** 확인하지 않으면 `@snu.ac.kr` 이라고 적기만
 * 하면 서울대가 된다.
 */
class UserEmailIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var userEmails: UserEmailRepository
    @Autowired private lateinit var verifications: EmailVerificationRepository
    @Autowired private lateinit var jdbcClient: JdbcClient

    private var userId: Long = 0
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("me@codekr.dev", "x", "나", setOf(UserRole.USER)))
        userId = user.id
        token = tokenProvider.issueAccessToken(user)
    }

    @Test
    fun `주소를 더하면 바로 붙지 않고 확인 메일이 간다`() {
        // 확인하지 않으면 적기만 하면 그 학교 사람이 된다.
        add("me@snu.ac.kr").andExpect(status().isAccepted)

        assertTrue(userEmails.findByUserIdOrderByIdAsc(userId).isEmpty(), "확인 전에는 붙지 않아야 합니다")
        assertEquals(1, verifications.findAll().count { it.email == "me@snu.ac.kr" })
    }

    @Test
    fun `확인하면 목록에 뜬다`() {
        add("me@snu.ac.kr")
        verifyLatest()

        mockMvc.perform(get("/api/v1/users/me/emails").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].email").value("me@snu.ac.kr"))
    }

    @Test
    fun `여러 개를 가질 수 있다`() {
        // **학부와 대학원, 학교와 회사를 동시에 가질 수 있다** — 기획서 4절의 결정이다.
        add("me@snu.ac.kr"); verifyLatest()
        add("me@kakao.com"); verifyLatest()

        assertEquals(2, userEmails.findByUserIdOrderByIdAsc(userId).size)
    }

    @Test
    fun `추가 주소를 확인해도 로그인 주소의 확인 여부는 그대로다`() {
        // 학교 메일을 확인했다고 가입 주소가 확인된 것은 아니다.
        add("me@snu.ac.kr")
        verifyLatest()

        assertEquals(null, userRepository.findById(userId).get().emailVerifiedAt)
    }

    @Test
    fun `이미 누가 쓰는 주소는 거절한다`() {
        /*
          같은 학교 메일로 두 계정이 같은 소속을 얻으면 안 된다.
          **남의 로그인 주소도 마찬가지다** — 그것을 내 추가 주소로 확인해 버리면
          그 사람의 소속을 가져가는 셈이다.
        */
        userRepository.save(User("other@codekr.dev", "x", "남", setOf(UserRole.USER)))

        add("other@codekr.dev").andExpect(status().isConflict)

        add("me@snu.ac.kr"); verifyLatest()
        add("me@snu.ac.kr").andExpect(status().isConflict)
    }

    @Test
    fun `형식이 아니면 보내기 전에 막는다`() {
        // 바깥으로 나가고 돈이 드는 호출이다 (#233).
        add("골뱅이가 없다").andExpect(status().isBadRequest)
    }

    @Test
    fun `남의 주소는 뗄 수 없다`() {
        add("me@snu.ac.kr"); verifyLatest()
        val id = userEmails.findByUserIdOrderByIdAsc(userId).first().id

        val otherToken = tokenProvider.issueAccessToken(
            userRepository.save(User("other@codekr.dev", "x", "남", setOf(UserRole.USER))),
        )
        mockMvc.perform(delete("/api/v1/users/me/emails/$id").header("Authorization", "Bearer $otherToken"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `떼면 목록에서 사라진다`() {
        add("me@snu.ac.kr"); verifyLatest()
        val id = userEmails.findByUserIdOrderByIdAsc(userId).first().id

        mockMvc.perform(delete("/api/v1/users/me/emails/$id").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        assertTrue(userEmails.findByUserIdOrderByIdAsc(userId).isEmpty())
    }

    @Test
    fun `탈퇴하면 확인한 주소도 지운다`() {
        /*
          **학교·회사 메일이라 실명이 들어 있는 경우가 많다** (#140). 로그인 주소를
          지우면서 이것을 남기면 "식별 정보를 남기지 않는다" 가 반만 지켜진다.
        */
        add("me@snu.ac.kr"); verifyLatest()

        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        assertTrue(userEmails.findByUserIdOrderByIdAsc(userId).isEmpty())
    }

    private fun add(email: String) = mockMvc.perform(
        post("/api/v1/users/me/emails")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email"}"""),
    )

    /**
     * 방금 보낸 토큰으로 확인한다.
     *
     * 토큰은 해시로만 저장되므로(#233) 시험에서도 원문을 알 수 없다. 대신 그 흐름을
     * 서비스로 직접 부르지 않고 **DB 의 해시를 아는 값으로 바꿔** 실제 경로를 태운다.
     */
    private fun verifyLatest() {
        val id = jdbcClient.sql("SELECT max(id) FROM email_verifications").query(Long::class.java).single()
        // **알려진 원문의 해시를 넣어 두고 그 원문으로 부른다.** 실제 확인 경로를 그대로 탄다.
        jdbcClient.sql("UPDATE email_verifications SET token_hash = :hash WHERE id = :id")
            .param("hash", sha256("test-token-$id")).param("id", id).update()

        mockMvc.perform(
            post("/api/v1/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"test-token-$id"}"""),
        ).andExpect(status().isNoContent)
    }

    private fun sha256(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
