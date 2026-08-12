package codekr.api.audit

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.security.ApiEndpointInventory
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals

/**
 * 어드민 관리 기록 (#225).
 *
 * **로그로는 안 된다** — 지나가고, 찾을 수 없고, 화면에 없다. 여기서 확인하는 것은
 * "이 회원에게 무슨 일이 있었나" 와 "이 어드민이 무엇을 했나" 둘 다 답하는지다.
 */
class AdminAuditIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var superuserToken: String
    private lateinit var adminToken: String
    private var actorId: Long = 0
    private var targetId: Long = 0

    @BeforeEach
    fun setUp() {
        val root = userRepository.save(User("root@codekr.dev", "x", "최고관리자", setOf(UserRole.SUPERUSER)))
        actorId = root.id
        superuserToken = tokenProvider.issueAccessToken(root)
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.ADMIN))),
        )
        targetId = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER))).id
    }

    @Test
    fun `역할을 바꾸면 기록이 남는다`() {
        changeRoles()

        mockMvc.perform(
            get("/api/v1/admin/audit-logs").param("targetUserId", targetId.toString())
                .header("Authorization", "Bearer $superuserToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].action").value("ROLE_CHANGE"))
            .andExpect(jsonPath("$.content[0].actorNickname").value("최고관리자"))
            // 무엇으로 바뀌었는지가 남아야 읽을 수 있는 기록이 된다.
            .andExpect(jsonPath("$.content[0].detail").value("USER, PROBLEM_SETTER"))
    }

    @Test
    fun `이 어드민이 무엇을 했나로도 찾는다`() {
        changeRoles()

        mockMvc.perform(
            get("/api/v1/admin/audit-logs").param("actorId", actorId.toString())
                .header("Authorization", "Bearer $superuserToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `강제 탈퇴는 사유가 없으면 막힌다`() {
        // **되돌릴 수 없는 조치다.** 계정이 사라진 뒤에 "누가 왜" 를 물으면 사유가
        // 유일한 답이라, 없으면 아예 실행되지 않아야 한다.
        mockMvc.perform(
            delete("/api/v1/admin/users/$targetId").header("Authorization", "Bearer $superuserToken"),
        ).andExpect(status().isBadRequest)

        // 막혔으므로 계정은 그대로다.
        mockMvc.perform(
            get("/api/v1/admin/users/$targetId").header("Authorization", "Bearer $superuserToken"),
        ).andExpect(jsonPath("$.withdrawnAt").doesNotExist())
    }

    @Test
    fun `강제 탈퇴 기록은 지워진 닉네임을 사본으로 남긴다`() {
        mockMvc.perform(
            delete("/api/v1/admin/users/$targetId").param("reason", "스팸 계정")
                .header("Authorization", "Bearer $superuserToken"),
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/v1/admin/audit-logs").param("targetUserId", targetId.toString())
                .header("Authorization", "Bearer $superuserToken"),
        )
            .andExpect(jsonPath("$.content[0].action").value("FORCE_WITHDRAW"))
            .andExpect(jsonPath("$.content[0].reason").value("스팸 계정"))
            // **탈퇴는 닉네임을 그 자리에서 지운다** (#140). 사본이 없으면 숫자만 남는다.
            .andExpect(jsonPath("$.content[0].targetLabel").value("풀이왕"))
    }

    @Test
    fun `어드민은 기록을 볼 수 없다`() {
        // 회원 목록은 어드민까지 열려 있지만(#223) 기록은 어드민끼리 서로를 보는 것이다.
        mockMvc.perform(get("/api/v1/admin/audit-logs").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `기록을 고치거나 지우는 길이 없다`() {
        // 감사 기록은 **덧붙이기만 되는 것**이어야 한다 (#225).
        //
        // 눌러서 확인하지 않고 **선언 목록으로** 확인한다 — 없는 경로를 부르면 지금
        // 500 이 나는데(별도 이슈), 그 오류에 기대면 오류 처리가 바뀔 때 이 시험이
        // 뜻을 잃는다. 여기서 지켜야 할 것은 "쓰기 경로가 선언되지 않았다" 이다.
        val writes = ApiEndpointInventory.ALL.filter {
            it.pattern.startsWith("/api/v1/admin/audit-logs") && it.method != "GET"
        }

        assertEquals(emptyList(), writes)
    }

    private fun changeRoles() {
        mockMvc.perform(
            put("/api/v1/admin/users/$targetId/roles")
                .header("Authorization", "Bearer $superuserToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"roles":["PROBLEM_SETTER"]}"""),
        ).andExpect(status().isOk)
    }
}
