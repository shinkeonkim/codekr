package codekr.api.affiliation

import codekr.api.affiliation.repository.UserAffiliationRepository
import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 소속 붙이기 (#398, #240 3단계).
 *
 * **확인한 주소가 있어야 붙는다.** 확인하지 않으면 `@snu.ac.kr` 이라고 적기만 하면
 * 서울대가 된다 — 그것이 #396 을 이 이슈의 선행으로 둔 이유다.
 */
class UserAffiliationIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var userAffiliations: UserAffiliationRepository

    private var userId: Long = 0
    private lateinit var token: String
    private var snu: Long = 0
    private var kakao: Long = 0

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("me@codekr.dev", "x", "나", setOf(UserRole.USER)))
        userId = user.id
        token = tokenProvider.issueAccessToken(user)
        snu = affiliation("서울대학교", "SCHOOL", "snu.ac.kr")
        kakao = affiliation("카카오", "COMPANY", "kakaocorp.com")
    }

    @Test
    fun `확인한 주소가 없으면 붙일 것도 없다`() {
        mockMvc.perform(get("/api/v1/users/me/affiliations").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.attached.length()").value(0))
            .andExpect(jsonPath("$.attachable.length()").value(0))
    }

    @Test
    fun `확인한 주소의 도메인이 소속을 가리키면 붙일 수 있다`() {
        verifiedEmail("me@snu.ac.kr")

        mockMvc.perform(get("/api/v1/users/me/affiliations").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.attachable.length()").value(1))
            .andExpect(jsonPath("$.attachable[0].name").value("서울대학교"))
    }

    @Test
    fun `붙이면 프로필에 뜬다`() {
        verifiedEmail("me@snu.ac.kr")
        attach(snu).andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/users/나"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.affiliations[0].name").value("서울대학교"))
            .andExpect(jsonPath("$.affiliations[0].kindLabel").value("학교"))
    }

    @Test
    fun `여럿을 동시에 가질 수 있다`() {
        // **학부와 대학원, 학교와 회사를 동시에** — 기획서 4절의 결정이다.
        verifiedEmail("me@snu.ac.kr")
        verifiedEmail("me@kakaocorp.com")
        attach(snu); attach(kakao)

        mockMvc.perform(get("/api/v1/users/me/affiliations").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.attached.length()").value(2))
    }

    @Test
    fun `주소를 확인하지 않은 소속은 붙일 수 없다`() {
        /*
          **화면이 보내는 값을 믿지 않는다.** 소속 id 만 받고 자격은 서버가 다시 찾는다 —
          화면을 안 거친 요청에도 같은 규칙이 걸려야 한다.
        */
        verifiedEmail("me@snu.ac.kr")

        attach(kakao).andExpect(status().isBadRequest)
    }

    @Test
    fun `프로필에는 주소가 나오지 않는다`() {
        // 소속은 남에게 보이는 것이지만 **어느 주소로 붙였는지는 아니다.**
        verifiedEmail("me@snu.ac.kr")
        attach(snu)

        val body = mockMvc.perform(get("/api/v1/users/나")).andReturn().response.contentAsString
        assertTrue(!body.contains("me@snu.ac.kr"), "주소가 새면 안 됩니다: $body")
    }

    @Test
    fun `주소를 떼면 소속도 함께 떨어진다`() {
        /*
          **붙인 근거가 사라졌기 때문이다.** DB 의 `ON DELETE CASCADE` 가 보장한다 —
          코드로만 지우면 다른 경로로 주소가 사라질 때 소속이 남는다.
        */
        val emailId = verifiedEmail("me@snu.ac.kr")
        attach(snu)
        assertEquals(1, userAffiliations.findByUserIdOrderByIdAsc(userId).size)

        mockMvc.perform(delete("/api/v1/users/me/emails/$emailId").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        assertTrue(userAffiliations.findByUserIdOrderByIdAsc(userId).isEmpty())
    }

    @Test
    fun `사용자가 뗄 수 있다`() {
        verifiedEmail("me@snu.ac.kr")
        attach(snu)

        mockMvc.perform(delete("/api/v1/users/me/affiliations/$snu").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        assertTrue(userAffiliations.findByUserIdOrderByIdAsc(userId).isEmpty())
        // 뗀 뒤에는 다시 붙일 수 있다 — 주소는 그대로다.
        mockMvc.perform(get("/api/v1/users/me/affiliations").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.attachable.length()").value(1))
    }

    @Test
    fun `내려간 소속은 붙일 수 없다`() {
        // 도메인이 함께 떼어지므로 자연히 그렇다 (#397).
        verifiedEmail("me@snu.ac.kr")
        jdbcClient.sql("UPDATE affiliations SET deleted_at = now() WHERE id = :id").param("id", snu).update()
        jdbcClient.sql("DELETE FROM affiliation_domains WHERE affiliation_id = :id").param("id", snu).update()

        mockMvc.perform(get("/api/v1/users/me/affiliations").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.attachable.length()").value(0))
        attach(snu).andExpect(status().isBadRequest)
    }

    @Test
    fun `탈퇴하면 소속도 떨어진다`() {
        verifiedEmail("me@snu.ac.kr")
        attach(snu)

        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        assertTrue(userAffiliations.findByUserIdOrderByIdAsc(userId).isEmpty())
    }

    private fun attach(affiliationId: Long) = mockMvc.perform(
        post("/api/v1/users/me/affiliations/$affiliationId").header("Authorization", "Bearer $token"),
    )

    private fun affiliation(name: String, kind: String, domain: String): Long {
        val id = jdbcClient.sql("INSERT INTO affiliations (name, kind) VALUES (:n, :k) RETURNING id")
            .param("n", name).param("k", kind).query(Long::class.java).single()
        jdbcClient.sql("INSERT INTO affiliation_domains (affiliation_id, domain) VALUES (:a, :d)")
            .param("a", id).param("d", domain).update()
        return id
    }

    /** 확인 흐름은 #396 이 시험한다. 여기서는 확인된 상태를 바로 만든다. */
    private fun verifiedEmail(email: String): Long =
        jdbcClient.sql("INSERT INTO user_emails (user_id, email) VALUES (:u, :e) RETURNING id")
            .param("u", userId).param("e", email).query(Long::class.java).single()
}
