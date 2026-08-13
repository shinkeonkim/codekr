package codekr.api.affiliation

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 소속과 도메인 관리 (#397, #240 2단계).
 *
 * **목록이 있어야 인증이 붙는다.** 그리고 **잘못 넣으면 그 도메인을 가진 모두가 그
 * 소속을 얻는다** — 이 시험들이 지키는 것이 그것이다.
 */
class AdminAffiliationIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var affiliations: AffiliationRepository
    @Autowired private lateinit var domains: AffiliationDomainRepository

    private lateinit var adminToken: String
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.ADMIN))),
        )
        userToken = tokenProvider.issueAccessToken(
            userRepository.save(User("user@codekr.dev", "x", "남", setOf(UserRole.USER))),
        )
    }

    @Test
    fun `소속을 만들고 도메인을 붙인다`() {
        val id = create("서울대학교", "SCHOOL")
        addDomain(id, "snu.ac.kr").andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/admin/affiliations").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("서울대학교"))
            .andExpect(jsonPath("$[0].kindLabel").value("학교"))
            .andExpect(jsonPath("$[0].domains[0].domain").value("snu.ac.kr"))
    }

    @Test
    fun `도메인이 여럿일 수 있다`() {
        // postech.ac.kr 과 postech.edu 처럼.
        val id = create("포항공대", "SCHOOL")
        addDomain(id, "postech.ac.kr")
        addDomain(id, "postech.edu")

        assertEquals(2, domains.findByAffiliationIdOrderByDomainAsc(id).size)
    }

    @Test
    fun `사람이 흔히 하는 실수를 고쳐 받는다`() {
        /*
          `@snu.ac.kr` 이라고 적는 일이 잦다. 그대로 두면 **메일 주소의 도메인과 영영
          안 맞는다** — 인증이 조용히 실패하고 아무도 이유를 모른다.
        */
        val id = create("서울대학교", "SCHOOL")

        addDomain(id, "@SNU.ac.kr").andExpect(status().isCreated)

        assertEquals("snu.ac.kr", domains.findByAffiliationIdOrderByDomainAsc(id).first().domain)
    }

    @Test
    fun `메일 주소를 통째로 넣어도 도메인만 남는다`() {
        val id = create("카카오", "COMPANY")

        addDomain(id, "someone@kakaocorp.com").andExpect(status().isCreated)

        assertEquals("kakaocorp.com", domains.findByAffiliationIdOrderByDomainAsc(id).first().domain)
    }

    @Test
    fun `점이 없는 값은 도메인이 아니다`() {
        val id = create("어딘가", "COMPANY")

        listOf("localhost", "snu", " ", "snu.ac.kr.").forEach { bad ->
            addDomain(id, bad).andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `한 도메인은 한 소속에만 붙는다`() {
        // 둘에 붙으면 같은 메일로 두 소속을 얻는다.
        val snu = create("서울대학교", "SCHOOL")
        val fake = create("가짜대학교", "SCHOOL")
        addDomain(snu, "snu.ac.kr").andExpect(status().isCreated)

        addDomain(fake, "snu.ac.kr").andExpect(status().isConflict)
    }

    @Test
    fun `같은 이름의 소속을 두 번 만들 수 없다`() {
        create("서울대학교", "SCHOOL")

        mockMvc.perform(
            post("/api/v1/admin/affiliations").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"서울대학교","kind":"SCHOOL"}"""),
        ).andExpect(status().isConflict)
    }

    @Test
    fun `내리면 목록에서 빠지고 도메인도 함께 떨어진다`() {
        /*
          **행을 지우지 않는다** (ADR-0007) — 이미 붙은 사람들이 있다.
          다만 **도메인은 떼야 한다.** 남겨 두면 내린 소속에 새 사람이 계속 붙는다.
        */
        val id = create("없어질대학교", "SCHOOL")
        addDomain(id, "gone.ac.kr")

        mockMvc.perform(delete("/api/v1/admin/affiliations/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isNoContent)

        assertTrue(affiliations.findByDeletedAtIsNullOrderByNameAsc().none { it.name == "없어질대학교" })
        assertTrue(domains.findByAffiliationIdOrderByDomainAsc(id).isEmpty())
        // 행 자체는 남는다 — 붙어 있던 사람들의 소속이 무엇이었는지 알 수 있어야 한다.
        assertTrue(affiliations.findById(id).isPresent)
    }

    @Test
    fun `어드민이 아니면 볼 수도 만들 수도 없다`() {
        mockMvc.perform(get("/api/v1/admin/affiliations").header("Authorization", "Bearer $userToken"))
            .andExpect(status().isForbidden)
    }

    private fun create(name: String, kind: String): Long {
        val body = mockMvc.perform(
            post("/api/v1/admin/affiliations").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","kind":"$kind"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
    }

    private fun addDomain(affiliationId: Long, domain: String) = mockMvc.perform(
        post("/api/v1/admin/affiliations/$affiliationId/domains")
            .header("Authorization", "Bearer $adminToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"domain":"$domain"}"""),
    )
}
