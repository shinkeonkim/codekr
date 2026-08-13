package codekr.api.badge

import codekr.api.ranking.badge.BadgeEvent
import codekr.api.ranking.badge.BadgeEventType
import codekr.api.ranking.badge.BadgeRuleEngine
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 뱃지 규칙 엔진 (#202).
 *
 * **완료 조건은 "새 뱃지의 조건을 코드 변경 없이 넣으면 실제로 지급된다" 이다** —
 * 그것을 시험이 직접 한다. 규칙을 SQL 로 넣고, 이벤트를 보내고, 뱃지가 나오는지 본다.
 */
class BadgeRuleEngineIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var engine: BadgeRuleEngine
    @Autowired private lateinit var jdbc: JdbcClient

    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        userId = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER))).id
    }

    @Test
    fun `코드를 고치지 않고 새 뱃지를 넣으면 지급된다`() {
        // 문제 셋을 맞힌 사람에게 주는 새 뱃지 — **코드는 한 줄도 바뀌지 않는다.**
        jdbc.sql(
            """
            INSERT INTO badges (code, label, description, rule_key, sort_order)
            VALUES ('THREE_SOLVED', '세 문제', '세 문제를 맞혔습니다', 'THREE_SOLVED', 200)
            """,
        ).update()
        jdbc.sql(
            """
            INSERT INTO badge_rules (rule_key, event, conditions, code)
            VALUES ('THREE_SOLVED', 'PROBLEM_ACCEPTED',
                    '{"all": [{"measure": "accepted_problem_count", "op": ">=", "value": 3}]}',
                    'THREE_SOLVED')
            """,
        ).update()

        solve(3)
        engine.handle(BadgeEvent(BadgeEventType.PROBLEM_ACCEPTED, userId, problemId = 1))

        assertTrue("THREE_SOLVED" in codes())
    }

    @Test
    fun `조건을 못 채우면 주지 않고 그 사실이 남는다`() {
        jdbc.sql(
            """
            INSERT INTO badges (code, label, description, rule_key, sort_order)
            VALUES ('TEN_SOLVED', '열 문제', '열 문제를 맞혔습니다', 'TEN_SOLVED', 201)
            """,
        ).update()
        jdbc.sql(
            """
            INSERT INTO badge_rules (rule_key, event, conditions, code)
            VALUES ('TEN_SOLVED', 'PROBLEM_ACCEPTED',
                    '{"all": [{"measure": "accepted_problem_count", "op": ">=", "value": 10}]}',
                    'TEN_SOLVED')
            """,
        ).update()

        solve(2)
        engine.handle(BadgeEvent(BadgeEventType.PROBLEM_ACCEPTED, userId, problemId = 1))

        assertFalse("TEN_SOLVED" in codes())
        // **안 주면 "왜 안 나왔는지" 를 물었을 때 답할 수 있어야 한다.**
        val logged = jdbc.sql(
            "SELECT matched FROM badge_awards_log WHERE user_id = :id AND rule_key = 'TEN_SOLVED'",
        ).param("id", userId).query(Boolean::class.java).list()
        assertEquals(listOf(false), logged)
    }

    @Test
    fun `기존 넷이 규칙으로 동작한다`() {
        solve(1)
        engine.handle(BadgeEvent(BadgeEventType.PROBLEM_ACCEPTED, userId, problemId = 1))

        // 조건이 빈 규칙 — 이벤트가 곧 달성이다.
        assertTrue("FIRST_ACCEPT" in codes())
        // 아무도 먼저 맞히지 않았다.
        assertTrue("FIRST_SOLVER" in codes())
    }

    @Test
    fun `이미 가진 뱃지는 다시 평가하지 않는다`() {
        solve(1)
        repeat(2) { engine.handle(BadgeEvent(BadgeEventType.PROBLEM_ACCEPTED, userId, problemId = 1)) }

        // 두 번째 이벤트에서는 지표를 계산하지도 않으므로 기록이 늘지 않는다.
        val logged = jdbc.sql(
            "SELECT count(*) FROM badge_awards_log WHERE user_id = :id AND rule_key = 'FIRST_ACCEPT'",
        ).param("id", userId).query(Int::class.java).single()
        assertEquals(1, logged)
    }

    @Test
    fun `파라미터화된 뱃지는 이벤트의 그룹으로 코드를 만든다`() {
        solve(10)
        engine.handle(BadgeEvent(BadgeEventType.PROBLEM_ACCEPTED, userId, problemId = 1))

        assertTrue("CATEGORY_10_ALGORITHM" in codes())
    }

    @Test
    fun `깨진 규칙은 아무에게도 주지 않는다`() {
        jdbc.sql(
            """
            INSERT INTO badges (code, label, description, rule_key, sort_order)
            VALUES ('BROKEN', '깨진 것', '', 'BROKEN', 202)
            """,
        ).update()
        jdbc.sql(
            """
            INSERT INTO badge_rules (rule_key, event, conditions, code)
            VALUES ('BROKEN', 'PROBLEM_ACCEPTED', '{"all": "이건 배열이 아니다"}', 'BROKEN')
            """,
        ).update()

        solve(1)
        engine.handle(BadgeEvent(BadgeEventType.PROBLEM_ACCEPTED, userId, problemId = 1))

        // 잘못 주는 것보다 안 주는 것이 낫다.
        assertFalse("BROKEN" in codes())
        // 그리고 다른 규칙은 그대로 돈다 — 규칙 하나가 깨져도 전부 멈추지 않는다.
        assertTrue("FIRST_ACCEPT" in codes())
    }

    @Test
    fun `끈 규칙은 돌지 않는다`() {
        jdbc.sql("UPDATE badge_rules SET enabled = false WHERE rule_key = 'FIRST_ACCEPT'").update()

        solve(1)
        engine.handle(BadgeEvent(BadgeEventType.PROBLEM_ACCEPTED, userId, problemId = 1))

        assertFalse("FIRST_ACCEPT" in codes())
    }

    private fun codes(): List<String> =
        jdbc.sql("SELECT code FROM user_badges WHERE user_id = :id")
            .param("id", userId).query { rs, _ -> rs.getString("code") }.list()

    /** 문제를 만들고 맞힌 것으로 기록한다 — 채점 경로를 타지 않고 점수 표만 채운다. */
    private fun solve(count: Int) {
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
                VALUES (:userId, :problemId, 10, now())
                ON CONFLICT DO NOTHING
                """,
            ).param("userId", userId).param("problemId", problemId).update()
        }
    }
}
