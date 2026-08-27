package codekr.api.terms

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
 * 약관 동의와 개정 (#235).
 *
 * **동의한 사실이 남지 않으면 동의를 받은 것이 아니다.** 여기서 확인하는 것은
 * "언제, 어느 버전에" 가 남는지와, 개정했을 때 다시 받을 수 있는지다.
 */
class TermsIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbc: JdbcClient

    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        /*
            **시드가 넣은 판을 하루 전으로 밀어 둔다** (#670).

            시드의 `effective_at` 은 마이그레이션이 돈 시각이고, 그것은 이 시험이 도는
            시각과 **몇 초 차이**다. 아래 `revise` 의 개정판이 그 좁은 틈에 걸리면 판이
            뒤집힌다 — 시드보다 앞서면 `findEffective` 가 시드를 고르고, 지금보다
            뒤면 아직 시행 전인 것이 된다.

            **JVM 과 Postgres 컨테이너의 시계가 정확히 같지 않다는 것이 그 틈을 만든다.**
            `effective_at` 은 Postgres 의 `now()` 로 넣고 비교는 JVM 의 `Instant.now()` 로
            한다. 하루를 벌어 두면 어느 쪽이 얼마나 앞서든 상관이 없다.
        */
        jdbc.sql("UPDATE term_documents SET effective_at = now() - interval '1 day' WHERE version = '1.0'")
            .update()

        token = tokenProvider.issueAccessToken(
            userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER))),
        )
    }

    @Test
    fun `시행 중인 약관을 로그인 없이 읽는다`() {
        // 가입하기 전에 읽어야 하는 문서다.
        mockMvc.perform(get("/api/v1/terms"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].version").value("1.0"))
    }

    @Test
    fun `필수 약관에 동의하지 않으면 가입되지 않는다`() {
        mockMvc.perform(
            post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"new@codekr.dev","password":"password1234","nickname":"새사람"}"""),
        ).andExpect(status().isBadRequest)

        // 동의 없이 만들어진 계정이 남으면 안 된다.
        kotlin.test.assertNull(userRepository.findByEmail("new@codekr.dev"))
    }

    @Test
    fun `가입하면 동의가 버전과 함께 남는다`() {
        mockMvc.perform(
            post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(signupBody("new@codekr.dev", "password1234", "새사람")),
        ).andExpect(status().isCreated)

        val created = userRepository.findByEmail("new@codekr.dev")!!
        val rows = jdbc.sql(
            """
            SELECT d.kind, d.version FROM term_agreements a
            JOIN term_documents d ON d.id = a.document_id
            WHERE a.user_id = :id ORDER BY d.kind
            """,
        ).param("id", created.id).query { rs, _ -> rs.getString("kind") + " " + rs.getString("version") }.list()

        kotlin.test.assertEquals(listOf("PRIVACY 1.0", "SERVICE 1.0"), rows)
    }

    @Test
    fun `개정하면 다시 받아야 할 것으로 뜬다`() {
        agreeAll()
        mockMvc.perform(get("/api/v1/terms/pending").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.length()").value(0))

        revise("2.0", "now() - interval '1 hour'")

        mockMvc.perform(get("/api/v1/terms/pending").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].version").value("2.0"))
    }

    @Test
    fun `시행일이 미래면 아직 받지 않는다`() {
        // 개정을 미리 넣어 두고 날짜에 맞춰 켜기 위해서다.
        revise("3.0", "now() + interval '30 days'")

        mockMvc.perform(get("/api/v1/terms"))
            .andExpect(jsonPath("$[?(@.version == '3.0')]").isEmpty)
    }

    @Test
    fun `다시 동의하면 목록에서 빠진다`() {
        agreeAll()
        revise("2.0", "now() - interval '1 hour'")

        val pendingId = jdbc.sql("SELECT id FROM term_documents WHERE version = '2.0'")
            .query(Long::class.java).single()

        mockMvc.perform(
            post("/api/v1/terms/agreements").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"documentIds":[$pendingId]}"""),
        ).andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/terms/pending").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `내가 동의한 내역을 본다`() {
        agreeAll()

        mockMvc.perform(get("/api/v1/terms/agreements").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].agreedAt").exists())
    }

    private fun revise(version: String, effectiveAt: String) {
        jdbc.sql(
            """
            INSERT INTO term_documents (kind, version, title, body, effective_at, required, reconsent)
            VALUES ('SERVICE', '$version', '서비스 이용약관', '바뀐 본문', $effectiveAt, true, true)
            """,
        ).update()
    }

    private fun agreeAll() {
        val ids = jdbc.sql("SELECT id FROM term_documents WHERE effective_at <= now()")
            .query(Long::class.java).list()
        mockMvc.perform(
            post("/api/v1/terms/agreements").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"documentIds":$ids}"""),
        ).andExpect(status().isNoContent)
    }
}
