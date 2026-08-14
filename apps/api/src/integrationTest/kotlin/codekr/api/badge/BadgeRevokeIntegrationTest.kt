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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 규칙을 좁히면 자격 잃은 뱃지를 거둔다 (#558).
 *
 * **#41 의 "회수하지 않는다" 를 뒤집는 자리다.** 원래 반대 근거가 "회수하면 사용자는
 * 그 이유를 알 길이 없다" 였으므로, **알림이 이 결정의 전제**다 — 거두기만 하고
 * 알리지 않으면 뒤집을 이유가 없어진다.
 */
class BadgeRevokeIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbc: JdbcClient

    private lateinit var superToken: String
    private var holderId: Long = 0

    @BeforeEach
    fun setUp() {
        superToken = tokenProvider.issueAccessToken(
            userRepository.save(User("root@codekr.dev", "x", "최고관리자", setOf(UserRole.SUPERUSER))),
        )
        holderId = userRepository.save(User("holder@codekr.dev", "x", "가진사람", setOf(UserRole.USER))).id

        jdbc.sql("INSERT INTO badges (code, label, description, rule_key) VALUES ('REVOKE_TEST', '시험', '시험용', 'revoke-test')")
            .update()
        // 조건과 무관하게 이미 갖고 있는 상태로 둔다 — 거두는 것이 이 시험의 대상이다.
        jdbc.sql("INSERT INTO user_badges (user_id, code) VALUES (:id, 'REVOKE_TEST')").param("id", holderId).update()
        createRule(threshold = 0)
    }

    @Test
    fun `조건을 좁히면 자격 잃은 사람에게서 거둔다`() {
        // 아무 문제도 안 푼 사람이라 100문제 조건에는 해당하지 않는다.
        updateRule(threshold = 100).andExpect(status().isOk)

        assert(!holdsBadge()) { "자격을 잃었는데 뱃지가 남아 있다" }
    }

    @Test
    fun `거둘 때 반드시 알린다`() {
        // **이것이 #41 을 뒤집는 조건이다.** 알리지 않으면 사용자는 왜 사라졌는지 모른다.
        updateRule(threshold = 100).andExpect(status().isOk)

        val notified = jdbc
            .sql("SELECT count(*) FROM notifications WHERE user_id = :id AND category = 'BADGE'")
            .param("id", holderId)
            .query(Int::class.java)
            .single()
        assert(notified == 1) { "알림이 $notified 건이다. 1건이어야 한다" }
    }

    @Test
    fun `거둔 것도 기록에 남는다`() {
        // 수여만 남기면 사라진 이유를 나중에 찾을 수 없다.
        updateRule(threshold = 100).andExpect(status().isOk)

        val logged = jdbc
            .sql("SELECT count(*) FROM badge_awards_log WHERE user_id = :id AND matched = false")
            .param("id", holderId)
            .query(Int::class.java)
            .single()
        assert(logged == 1) { "회수 기록이 $logged 건이다" }
    }

    @Test
    fun `자격이 그대로면 거두지 않는다`() {
        // 넓히거나 그대로면 아무 일도 없어야 한다 — 저장할 때마다 거두면 안 된다.
        updateRule(threshold = 0).andExpect(status().isOk)

        assert(holdsBadge()) { "자격이 그대로인데 거뒀다" }
    }

    @Test
    fun `미리보기가 몇 명이 잃는지 알려준다`() {
        // 저장하면 실제로 거두므로, 그 수를 저장 전에 알 수 있어야 한다 (#549 가 보인다).
        mockMvc.perform(
            post("/api/v1/admin/badge-rules/dry-run")
                .header("Authorization", "Bearer $superToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleBody(threshold = 100)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.losing").value(1))

        // 미리보기는 **아무것도 바꾸지 않는다.**
        assert(holdsBadge()) { "미리보기가 뱃지를 거뒀다" }
    }

    private fun holdsBadge(): Boolean =
        jdbc.sql("SELECT count(*) FROM user_badges WHERE user_id = :id AND code = 'REVOKE_TEST'")
            .param("id", holderId)
            .query(Int::class.java)
            .single() > 0

    private fun createRule(threshold: Int) =
        mockMvc.perform(
            post("/api/v1/admin/badge-rules")
                .header("Authorization", "Bearer $superToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleBody(threshold)),
        ).andExpect(status().isCreated)

    private fun updateRule(threshold: Int) =
        mockMvc.perform(
            put("/api/v1/admin/badge-rules/revoke-test")
                .header("Authorization", "Bearer $superToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleBody(threshold)),
        )

    private fun ruleBody(threshold: Int) = """
        {
          "ruleKey": "revoke-test",
          "event": "PROBLEM_ACCEPTED",
          "code": "REVOKE_TEST",
          "conditions": [{"measure": "accepted_problem_count", "op": ">=", "value": $threshold}]
        }
    """.trimIndent()
}
