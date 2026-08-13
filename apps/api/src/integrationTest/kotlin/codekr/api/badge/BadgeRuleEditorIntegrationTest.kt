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
 * 뱃지 규칙 편집기 (#203).
 *
 * **저장 전에 결과를 볼 수 있어야 한다.** 규칙은 사용자에게 보이는 것을 바꾸는데,
 * 저장한 뒤에야 알면 되돌릴 방법이 뱃지 회수뿐이고 그것은 하지 않기로 했다(#41).
 */
class BadgeRuleEditorIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbc: JdbcClient

    private lateinit var superToken: String
    private lateinit var adminToken: String
    private var solverId: Long = 0

    @BeforeEach
    fun setUp() {
        superToken = tokenProvider.issueAccessToken(
            userRepository.save(User("root@codekr.dev", "x", "최고관리자", setOf(UserRole.SUPERUSER))),
        )
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.ADMIN))),
        )
        solverId = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER))).id
    }

    @Test
    fun `화면이 쓸 목록을 서버가 내려준다`() {
        // **화면이 이벤트·지표를 하드코딩하지 않는다** — 늘 때마다 화면을 고치지 않는다.
        mockMvc.perform(get("/api/v1/admin/badge-rules/vocabulary").header("Authorization", "Bearer $superToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.events[0]").value("PROBLEM_ACCEPTED"))
            .andExpect(jsonPath("$.measures[?(@.name == 'is_first_solver')].type").value("boolean"))
            .andExpect(jsonPath("$.operators").isNotEmpty)
    }

    @Test
    fun `틀린 자리를 짚어 준다`() {
        // "잘못된 규칙입니다" 로는 고칠 수 없다.
        mockMvc.perform(
            post("/api/v1/admin/badge-rules/dry-run").header("Authorization", "Bearer $superToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"ruleKey":"BAD","event":"NO_SUCH_EVENT","code":"BAD",
                       "conditions":[{"measure":"no_such_measure","op":"~~","value":1}]}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errors.length()").value(3))
    }

    @Test
    fun `이벤트에 없는 지표를 막는다`() {
        // `is_first_solver` 는 이벤트 지표라 STREAK_UPDATED 에서는 언제나 거짓이 된다.
        mockMvc.perform(
            post("/api/v1/admin/badge-rules/dry-run").header("Authorization", "Bearer $superToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"ruleKey":"X","event":"STREAK_UPDATED","code":"X",
                       "conditions":[{"measure":"is_first_solver","op":"==","value":true}]}""",
                ),
        )
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("쓸 수 없습니다")))
    }

    @Test
    fun `저장하지 않고 누가 받는지 본다`() {
        solve(solverId, 3)

        mockMvc.perform(
            post("/api/v1/admin/badge-rules/dry-run").param("userId", solverId.toString())
                .header("Authorization", "Bearer $superToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"ruleKey":"THREE","event":"PROBLEM_ACCEPTED","code":"THREE",
                       "conditions":[{"measure":"accepted_problem_count","op":">=","value":3}]}""",
                ),
        )
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.matchesUser").value(true))
            .andExpect(jsonPath("$.matched").value(1))

        // **저장되지 않았다.**
        val count = jdbc.sql("SELECT count(*) FROM badge_rules WHERE rule_key = 'THREE'")
            .query(Int::class.java).single()
        kotlin.test.assertEquals(0, count)
    }

    @Test
    fun `그 사람이 못 받으면 그렇게 답한다`() {
        solve(solverId, 1)

        mockMvc.perform(
            post("/api/v1/admin/badge-rules/dry-run").param("userId", solverId.toString())
                .header("Authorization", "Bearer $superToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"ruleKey":"TEN","event":"PROBLEM_ACCEPTED","code":"TEN",
                       "conditions":[{"measure":"accepted_problem_count","op":">=","value":10}]}""",
                ),
        )
            .andExpect(jsonPath("$.matchesUser").value(false))
    }

    @Test
    fun `잘못 쓴 규칙은 저장되지 않는다`() {
        mockMvc.perform(
            post("/api/v1/admin/badge-rules").header("Authorization", "Bearer $superToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"ruleKey":"BAD","event":"PROBLEM_ACCEPTED","code":"BAD_{group}",
                       "conditions":[]}""",
                ),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `규칙을 켜고 끈다`() {
        // 지우는 것보다 끄는 것이 먼저다.
        mockMvc.perform(
            put("/api/v1/admin/badge-rules/FIRST_ACCEPT/enabled").param("enabled", "false")
                .header("Authorization", "Bearer $superToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))
    }

    @Test
    fun `어드민이어도 최고 관리자가 아니면 못 만진다`() {
        // 뱃지는 모두에게 보이는 것이라 아무 어드민이나 바꾸면 안 된다.
        mockMvc.perform(get("/api/v1/admin/badge-rules").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isForbidden)
    }

    private fun solve(userId: Long, count: Int) {
        repeat(count) { index ->
            val problemId = (index + 1).toLong()
            jdbc.sql(
                """
                INSERT INTO problems (id, slug, title, category, description, time_limit_ms,
                                      memory_limit_mb, published, difficulty_state)
                VALUES (:id, 'p-' || :id, '문제 ' || :id, 'ALGORITHM', '지문', 2000, 256, true, 'UNRATED')
                ON CONFLICT (id) DO NOTHING
                """,
            ).param("id", problemId).update()
            jdbc.sql(
                """
                INSERT INTO user_problem_scores (user_id, problem_id, score, solved_at)
                VALUES (:userId, :problemId, 10, now()) ON CONFLICT DO NOTHING
                """,
            ).param("userId", userId).param("problemId", problemId).update()
        }
    }
}
