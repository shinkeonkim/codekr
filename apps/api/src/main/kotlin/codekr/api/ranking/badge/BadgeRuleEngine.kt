package codekr.api.ranking.badge

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * 규칙을 돌려 뱃지를 준다 (#202, 설계는 #200).
 *
 * **`BadgeAwarder` 를 대신한다** — 뱃지 종류가 코드에 박혀 있지 않다. 새 뱃지의 조건은
 * `badge_rules` 에 한 줄을 넣으면 된다.
 *
 * **뱃지는 곁가지다.** 규칙 평가가 실패해도 채점이나 제출이 막히면 안 된다.
 */
@Component
class BadgeRuleEngine(
    private val jdbcClient: JdbcClient,
    private val measures: BadgeMeasures,
    private val badgeRepository: BadgeRepository,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 이벤트 하나를 처리한다.
     *
     * **부르는 트랜잭션에 함께 든다.** 기획(#200)은 "밖으로 빼는 쪽" 을 봤지만, 실제로
     * 빼 보니 **지표가 방금 쓴 점수를 보지 못한다** — `user_problem_scores` 는 아직
     * 커밋되지 않은 바깥 트랜잭션 안에 있어서, 카테고리 수도 최초 해결자도 어긋난다.
     * 통합 시험이 그것을 잡았다.
     *
     * 대신 **실패를 밖으로 내보내지 않는다.** 여기서 잡으므로 트랜잭션이 롤백으로
     * 표시되지 않고, 뱃지 때문에 채점이 사라지지 않는다.
     *
     * 그래서 **빨라야 한다** — 이미 가진 뱃지는 지표를 계산하기도 전에 건너뛰고,
     * 지표는 이벤트 안에서 한 번만 잰다.
     */
    @Transactional
    fun handle(event: BadgeEvent) {
        runCatching { evaluate(event) }
            .onFailure { log.error("뱃지 규칙 평가 실패 event={} user={}", event.type, event.userId, it) }
    }

    private fun evaluate(event: BadgeEvent) {
        val rules = rulesFor(event.type)
        if (rules.isEmpty()) return

        // 같은 이벤트 안에서 지표를 한 번만 계산한다 (#200 §6).
        val cache = mutableMapOf<String, Any?>()
        val owned = badgeRepository.codesOf(event.userId)

        for (rule in rules) {
            val group = rule.groupBy?.let { measures.group(it, event) }
            // groupBy 가 있는데 그룹을 알 수 없으면 이번 이벤트로는 판정할 수 없다.
            if (rule.groupBy != null && group == null) continue

            val code = rule.code.replace("{group}", group ?: "")
            /*
                **이미 가진 뱃지는 지표를 계산하기 전에 건너뛴다** (#200 §6).

                정답을 100번 맞힌 사람에게 `FIRST_ACCEPT` 조건을 100번 확인할 이유가 없다.
                규칙 수가 늘수록 이것이 가장 크게 듣는다.
            */
            if (code in owned) continue

            val matched = rule.conditions.all { condition ->
                matches(measures.measure(condition.measure, event, cache), condition)
            }
            record(event.userId, rule.ruleKey, code, matched)
            if (matched) badgeRepository.award(event.userId, code)
        }
    }

    private fun matches(actual: Any?, condition: BadgeCondition): Boolean {
        if (actual == null) return false
        return when (condition.op) {
            "==" -> actual.toString() == condition.value.toString()
            ">=" -> asNumber(actual) >= asNumber(condition.value)
            ">" -> asNumber(actual) > asNumber(condition.value)
            // 모르는 연산자는 만족하지 않은 것으로 본다 — 규칙이 틀렸다고 뱃지를 뿌리지 않는다.
            else -> false
        }
    }

    private fun asNumber(value: Any?): Double = when (value) {
        is Number -> value.toDouble()
        is Boolean -> if (value) 1.0 else 0.0
        else -> value?.toString()?.toDoubleOrNull() ?: Double.NEGATIVE_INFINITY
    }

    /** 준 것과 **안 준 것**을 함께 남긴다 — "왜 안 나왔는지" 를 물으면 여기서 답한다. */
    private fun record(userId: Long, ruleKey: String, code: String, matched: Boolean) {
        jdbcClient.sql(
            "INSERT INTO badge_awards_log (user_id, rule_key, code, matched) VALUES (:u, :r, :c, :m)",
        )
            .param("u", userId).param("r", ruleKey).param("c", code).param("m", matched)
            .update()
    }

    private fun rulesFor(type: BadgeEventType): List<BadgeRule> =
        jdbcClient.sql(
            "SELECT rule_key, conditions, group_by, code FROM badge_rules WHERE event = :event AND enabled",
        )
            .param("event", type.name)
            .query { rs, _ ->
                BadgeRule(
                    ruleKey = rs.getString("rule_key"),
                    conditions = parse(rs.getString("conditions")),
                    groupBy = rs.getString("group_by"),
                    code = rs.getString("code"),
                )
            }
            .list()

    private fun parse(json: String): List<BadgeCondition> =
        runCatching {
            val all = objectMapper.readTree(json).get("all")
            /*
                **빈 조건과 깨진 조건을 갈라야 한다.**

                조건이 비어 있는 것은 뜻이 있다 — "이벤트가 곧 달성" 이다(#200 §5의
                `FIRST_ACCEPT`). 그래서 `all` 이 배열이 아닌 것을 그냥 넘기면 **깨진
                규칙이 "언제나 준다" 로 읽힌다.** 시험이 그것을 잡았다.
            */
            require(all == null || all.isArray) { "조건의 all 은 배열이어야 합니다" }
            val parsed = mutableListOf<BadgeCondition>()
            all?.forEach { node ->
                val value: Any = if (node.get("value").isBoolean) {
                    node.get("value").asBoolean()
                } else {
                    node.get("value").asDouble()
                }
                parsed += BadgeCondition(node.get("measure").asString(), node.get("op").asString(), value)
            }
            parsed.toList()
        }.getOrElse {
            // 규칙이 깨져 있으면 **아무에게도 주지 않는다.** 잘못 주는 것보다 낫다.
            log.error("뱃지 규칙을 읽지 못했습니다: {}", json, it)
            listOf(BadgeCondition("__broken", "==", true))
        }
}

data class BadgeRule(
    val ruleKey: String,
    val conditions: List<BadgeCondition>,
    val groupBy: String?,
    val code: String,
)

data class BadgeCondition(val measure: String, val op: String, val value: Any)
