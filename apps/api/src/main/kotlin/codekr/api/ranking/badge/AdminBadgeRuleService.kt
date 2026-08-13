package codekr.api.ranking.badge

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/** 규칙을 검증하고 저장한다 (#203). */
@Service
class AdminBadgeRuleService(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
    private val measures: BadgeMeasures,
) {

    fun vocabulary() = BadgeVocabulary(
        events = BadgeEventType.entries.map { it.name },
        measures = MEASURES,
        operators = OPERATORS,
        groupBys = listOf("problem_category"),
    )

    fun findAll(): List<BadgeRuleResponse> =
        jdbcClient.sql("SELECT rule_key, event, code, group_by, conditions, enabled FROM badge_rules ORDER BY rule_key")
            .query { rs, _ ->
                BadgeRuleResponse(
                    ruleKey = rs.getString("rule_key"),
                    event = rs.getString("event"),
                    code = rs.getString("code"),
                    groupBy = rs.getString("group_by"),
                    conditions = readConditions(rs.getString("conditions")),
                    enabled = rs.getBoolean("enabled"),
                )
            }
            .list()

    /**
     * 저장하지 않고 확인한다 (#203).
     *
     * **표본만 본다.** "몇 명이 받는가" 는 전체를 훑는 질의라 운영 DB 에서 그냥 돌리면
     * 느려진다 — 최근 활동한 사람 [SAMPLE_SIZE] 명으로 "말이 되는 규칙인가" 를 본다.
     */
    fun dryRun(request: BadgeRuleUpsertRequest, userId: Long?): BadgeDryRunResponse {
        val errors = validate(request)
        if (errors.isNotEmpty()) {
            return BadgeDryRunResponse(valid = false, errors = errors, matched = 0, sampled = 0, matchesUser = null)
        }

        val sample = sampleUsers()
        val matched = sample.count { evaluate(request, it) }
        return BadgeDryRunResponse(
            valid = true,
            errors = emptyList(),
            matched = matched,
            sampled = sample.size,
            matchesUser = userId?.let { evaluate(request, it) },
        )
    }

    @Transactional
    fun create(request: BadgeRuleUpsertRequest): BadgeRuleResponse {
        requireValid(request)
        jdbcClient.sql(
            """
            INSERT INTO badge_rules (rule_key, event, conditions, group_by, code)
            VALUES (:ruleKey, :event, :conditions::jsonb, :groupBy, :code)
            """,
        )
            .param("ruleKey", request.ruleKey)
            .param("event", request.event)
            .param("conditions", conditionsJson(request))
            .param("groupBy", request.groupBy)
            .param("code", request.code)
            .update()
        return findOne(request.ruleKey)
    }

    @Transactional
    fun update(ruleKey: String, request: BadgeRuleUpsertRequest): BadgeRuleResponse {
        requireValid(request)
        jdbcClient.sql(
            """
            UPDATE badge_rules
            SET event = :event, conditions = :conditions::jsonb, group_by = :groupBy,
                code = :code, updated_at = now()
            WHERE rule_key = :ruleKey
            """,
        )
            .param("ruleKey", ruleKey)
            .param("event", request.event)
            .param("conditions", conditionsJson(request))
            .param("groupBy", request.groupBy)
            .param("code", request.code)
            .update()
        return findOne(ruleKey)
    }

    @Transactional
    fun setEnabled(ruleKey: String, enabled: Boolean): BadgeRuleResponse {
        jdbcClient.sql("UPDATE badge_rules SET enabled = :enabled, updated_at = now() WHERE rule_key = :ruleKey")
            .param("ruleKey", ruleKey).param("enabled", enabled).update()
        return findOne(ruleKey)
    }

    /**
     * 문법 검증 (#203).
     *
     * **틀린 자리를 짚어 준다** — "잘못된 규칙입니다" 로는 고칠 수 없다.
     */
    private fun validate(request: BadgeRuleUpsertRequest): List<String> = buildList {
        if (BadgeEventType.entries.none { it.name == request.event }) {
            add("모르는 이벤트입니다: ${request.event} (가능: ${BadgeEventType.entries.joinToString()})")
        }
        request.conditions.forEachIndexed { index, condition ->
            val known = MEASURES.firstOrNull { it.name == condition.measure }
            if (known == null) {
                add("${index + 1}번 조건: 모르는 지표입니다 — ${condition.measure}")
            } else if (request.event !in known.events) {
                // 이벤트 지표는 그 이벤트에서만 뜻이 있다 (#200 §4.1).
                add("${index + 1}번 조건: ${condition.measure} 는 ${request.event} 에서 쓸 수 없습니다")
            }
            if (condition.op !in OPERATORS) {
                add("${index + 1}번 조건: 모르는 연산자입니다 — ${condition.op}")
            }
        }
        if (request.groupBy != null && "{group}" !in request.code) {
            add("groupBy 를 쓰면 코드에 {group} 이 있어야 합니다")
        }
        if (request.groupBy == null && "{group}" in request.code) {
            add("코드에 {group} 이 있으면 groupBy 를 정해야 합니다")
        }
    }

    private fun requireValid(request: BadgeRuleUpsertRequest) {
        val errors = validate(request)
        if (errors.isNotEmpty()) throw ApiException(ErrorCode.VALIDATION_ERROR, errors.joinToString(" / "))
    }

    /** 그 사람이 지금 이 규칙을 만족하는가. 이벤트 지표는 이벤트가 없으므로 건너뛴다. */
    private fun evaluate(request: BadgeRuleUpsertRequest, userId: Long): Boolean {
        val event = BadgeEvent(BadgeEventType.valueOf(request.event), userId)
        val cache = mutableMapOf<String, Any?>()
        return request.conditions.all { condition ->
            val actual = measures.measure(condition.measure, event, cache) ?: return@all false
            when (condition.op) {
                "==" -> actual.toString() == condition.value.toString()
                ">=" -> asNumber(actual) >= asNumber(condition.value)
                ">" -> asNumber(actual) > asNumber(condition.value)
                else -> false
            }
        }
    }

    private fun asNumber(value: Any?): Double = when (value) {
        is Number -> value.toDouble()
        is Boolean -> if (value) 1.0 else 0.0
        else -> value?.toString()?.toDoubleOrNull() ?: Double.NEGATIVE_INFINITY
    }

    private fun sampleUsers(): List<Long> =
        jdbcClient.sql("SELECT id FROM users WHERE withdrawn_at IS NULL ORDER BY id DESC LIMIT :limit")
            .param("limit", SAMPLE_SIZE)
            .query { rs, _ -> rs.getLong("id") }
            .list()

    private fun findOne(ruleKey: String): BadgeRuleResponse =
        findAll().firstOrNull { it.ruleKey == ruleKey }
            ?: throw ApiException(ErrorCode.VALIDATION_ERROR, "규칙을 찾을 수 없습니다: $ruleKey")

    private fun conditionsJson(request: BadgeRuleUpsertRequest): String =
        objectMapper.writeValueAsString(mapOf("all" to request.conditions))

    private fun readConditions(json: String): List<BadgeConditionRequest> = runCatching {
        val all = objectMapper.readTree(json).get("all")
        val parsed = mutableListOf<BadgeConditionRequest>()
        all?.forEach { node ->
            val value: Any = if (node.get("value").isBoolean) node.get("value").asBoolean() else node.get("value").asDouble()
            parsed += BadgeConditionRequest(node.get("measure").asString(), node.get("op").asString(), value)
        }
        parsed.toList()
    }.getOrElse { emptyList() }

    private companion object {
        /** 드라이런 표본. 전체를 훑으면 운영 DB 가 느려진다. */
        const val SAMPLE_SIZE = 200

        val OPERATORS = listOf(">=", ">", "==")

        /**
         * 쓸 수 있는 지표 (#200 §4.1).
         *
         * **이벤트 지표는 그 이벤트에서만 뜻이 있다** — `is_first_solver` 를
         * `STREAK_UPDATED` 에 걸면 언제나 거짓이 되므로, 아예 못 고르게 한다.
         */
        val MEASURES = listOf(
            MeasureInfo("accepted_problem_count", "맞힌 문제 수", "number", listOf("PROBLEM_ACCEPTED")),
            MeasureInfo("accepted_in_category", "이 분야에서 맞힌 수", "number", listOf("PROBLEM_ACCEPTED")),
            MeasureInfo("longest_streak_days", "최장 연속일", "number", listOf("STREAK_UPDATED", "PROBLEM_ACCEPTED")),
            MeasureInfo("is_first_solver", "최초 해결자인가", "boolean", listOf("PROBLEM_ACCEPTED")),
        )
    }
}
