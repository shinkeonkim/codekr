package codekr.api.user

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 어드민 회원 목록·검색 (#223).
 *
 * **읽기와 쓰기의 권한이 다르다.** 회원을 찾는 것과 역할을 바꾸는 것은 무게가 다르다 —
 * 전에는 어드민 회원 경로가 통째로 SUPERUSER 로 잠겨 있어 나눌 수가 없었다.
 */
class AdminUserSearchIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var adminToken: String
    private lateinit var superuserToken: String
    private lateinit var userToken: String
    private var targetId: Long = 0

    @BeforeEach
    fun setUp() {
        adminToken = token(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.ADMIN)))
        superuserToken = token(User("root@codekr.dev", "x", "최고관리자", setOf(UserRole.SUPERUSER)))
        userToken = token(User("member@codekr.dev", "x", "일반유저", setOf(UserRole.USER)))
        targetId = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER))).id
    }

    @Test
    fun `어드민은 회원을 찾을 수 있다`() {
        mockMvc.perform(get("/api/v1/admin/users").param("q", "풀이왕").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            // id 와 이메일을 함께 준다 — 사람을 특정하려면 둘 다 필요하다.
            .andExpect(jsonPath("$.content[0].id").value(targetId))
            .andExpect(jsonPath("$.content[0].email").value("solver@codekr.dev"))
    }

    @Test
    fun `이메일로도 찾는다`() {
        mockMvc.perform(get("/api/v1/admin/users").param("q", "solver@").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `한 글자로는 훑을 수 없다`() {
        // 목록에 이메일을 보이기로 했으므로 부분 일치 검색이 이메일 목록을 훑는 수단이
        // 된다. 한 글자를 막는 것만으로 그 값이 크게 떨어진다 (#223).
        mockMvc.perform(get("/api/v1/admin/users").param("q", "a").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `탈퇴한 회원은 기본으로 빠지고 켜면 보인다`() {
        val withdrawn = userRepository.save(User("gone@codekr.dev", "x", "떠난사람", setOf(UserRole.USER)))
        withdrawn.withdraw()
        userRepository.saveAndFlush(withdrawn)

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer $adminToken"))
            .andExpect(jsonPath("$.content[?(@.nickname == '떠난사람')]").isEmpty)

        mockMvc.perform(
            get("/api/v1/admin/users").param("includeWithdrawn", "true")
                .header("Authorization", "Bearer $adminToken"),
        )
            // 준비 단계의 넷 + 탈퇴한 하나.
            .andExpect(jsonPath("$.totalElements").value(5))
    }

    @Test
    fun `역할로 거른다`() {
        mockMvc.perform(
            get("/api/v1/admin/users").param("role", "SUPERUSER")
                .header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].nickname").value("최고관리자"))
    }

    @Test
    fun `상세는 점수와 활동을 함께 준다`() {
        mockMvc.perform(get("/api/v1/admin/users/$targetId").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nickname").value("풀이왕"))
            .andExpect(jsonPath("$.score").value(0))
            .andExpect(jsonPath("$.submissionCount").value(0))
    }

    @Test
    fun `일반 사용자는 회원 목록을 볼 수 없다`() {
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer $userToken"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `읽기는 어드민까지 열리고 쓰기는 최고 관리자만이다`() {
        // 이 갈림이 이 이슈의 핵심이다 (#223). 전에는 어드민 회원 경로가 통째로
        // SUPERUSER 로 잠겨 있어 어드민이 회원을 찾을 수조차 없었다.
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)

        // 역할 변경은 어드민이어도 막힌다.
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/admin/users/$targetId/roles")
                .header("Authorization", "Bearer $adminToken")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"roles":["USER"]}"""),
        )
            .andExpect(status().isForbidden)

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/admin/users/$targetId/roles")
                .header("Authorization", "Bearer $superuserToken")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"roles":["USER"]}"""),
        )
            .andExpect(status().isOk)
    }

    private fun token(user: User): String = tokenProvider.issueAccessToken(userRepository.save(user))
}
