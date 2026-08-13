package codekr.api.badge

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 뱃지 정의를 데이터로 (#201).
 *
 * **문구를 고치려고 배포하지 않는다.** 다만 그 대가로 문구가 조용히 바뀔 수 있다 —
 * 이미 받은 사람의 설명도 함께 바뀐다는 것을 시험이 그대로 드러낸다.
 */
class BadgeDefinitionIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbc: JdbcClient

    private lateinit var adminToken: String
    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.ADMIN))),
        )
        userId = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER))).id
    }

    @Test
    fun `정의가 표에서 온다`() {
        mockMvc.perform(get("/api/v1/badges"))
            .andExpect(status().isOk)
            // 마이그레이션이 지금 있는 것들을 넣었다 — 빈 상태로 시작하지 않는다.
            .andExpect(jsonPath("$[?(@.code == 'FIRST_ACCEPT')].label").value("첫 정답"))
            .andExpect(jsonPath("$[?(@.code == 'CATEGORY_10_SQL')].label").value("SQL 10문제"))
    }

    @Test
    fun `문구를 배포 없이 고친다`() {
        award("FIRST_ACCEPT")

        mockMvc.perform(
            put("/api/v1/admin/badges/FIRST_ACCEPT").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"label":"첫 걸음","description":"처음으로 정답을 받았습니다","sortOrder":10}"""),
        ).andExpect(status().isOk)

        // **이미 받은 사람에게도 바뀐다.** 사본을 두면 원래 구조로 돌아가므로 받아들인다.
        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer $adminToken"))
            .andExpect(jsonPath("$.badges[0].label").value("첫 걸음"))
    }

    @Test
    fun `숨기면 프로필에서 빠지고 다시 켜면 돌아온다`() {
        award("FIRST_ACCEPT")

        hide("FIRST_ACCEPT", visible = false)
        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer $adminToken"))
            .andExpect(jsonPath("$.badges.length()").value(0))

        // **지운 것이 아니다** — 부여 기록은 그대로다.
        hide("FIRST_ACCEPT", visible = true)
        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer $adminToken"))
            .andExpect(jsonPath("$.badges.length()").value(1))
    }

    @Test
    fun `순서를 정의가 정한다`() {
        award("STREAK_7")
        award("FIRST_ACCEPT")

        // 부여 시각이 아니라 정의의 순서다 — 받은 순서대로면 사람마다 목록이 달라진다.
        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer $adminToken"))
            .andExpect(jsonPath("$.badges[0].code").value("FIRST_ACCEPT"))
            .andExpect(jsonPath("$.badges[1].code").value("STREAK_7"))
    }

    @Test
    fun `정의가 없는 옛 코드도 죽지 않는다`() {
        // 지워진 규칙의 뱃지를 이미 가진 사람이 있을 수 있다.
        award("SOME_OLD_BADGE")

        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.badges[0].code").value("SOME_OLD_BADGE"))
            .andExpect(jsonPath("$.badges[0].label").value("SOME_OLD_BADGE"))
    }

    @Test
    fun `새 뱃지를 만든다`() {
        mockMvc.perform(
            post("/api/v1/admin/badges").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"code":"EVENT_2026","label":"2026 이벤트","description":"참가했습니다",
                       "ruleKey":"FIRST_ACCEPT","sortOrder":500}""",
                ),
        ).andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/badges"))
            .andExpect(jsonPath("$[?(@.code == 'EVENT_2026')].label").value("2026 이벤트"))
    }

    @Test
    fun `어드민이 아니면 정의를 고칠 수 없다`() {
        val userToken = tokenProvider.issueAccessToken(userRepository.findById(userId).orElseThrow())

        mockMvc.perform(
            put("/api/v1/admin/badges/FIRST_ACCEPT").header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"label":"내 마음대로","description":"","sortOrder":0}"""),
        ).andExpect(status().isForbidden)
    }

    private fun award(code: String) {
        jdbc.sql("INSERT INTO user_badges (user_id, code) VALUES (:id, :code) ON CONFLICT DO NOTHING")
            .param("id", userId).param("code", code).update()
    }

    private fun hide(code: String, visible: Boolean) {
        mockMvc.perform(
            put("/api/v1/admin/badges/$code").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"label":"첫 정답","description":"처음으로 문제를 맞혔습니다","visible":$visible,"sortOrder":10}"""),
        ).andExpect(status().isOk)
    }
}
