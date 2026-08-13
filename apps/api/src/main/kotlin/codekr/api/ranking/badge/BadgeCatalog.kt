package codekr.api.ranking.badge

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

/**
 * 뱃지 정의 (#201).
 *
 * 이름·설명·노출·순서를 **표에서 읽는다.** 오타 하나를 고치려고 배포하지 않는다.
 *
 * **모르는 코드가 와도 죽지 않는다.** 정의가 지워졌거나 옛 코드의 뱃지를 가진 사람이
 * 있을 수 있다 — 그때는 코드를 그대로 이름으로 쓴다. 지금 `Badge.describe` 가 하는
 * 일과 같고, 그 규칙이 사라지면 프로필이 깨진다.
 *
 * **캐시한다.** 프로필마다 읽히는데 바뀌는 일은 드물다 — 어드민이 고칠 때 비운다.
 */
@Component
class BadgeCatalog(private val jdbcClient: JdbcClient) {

    private val cache = AtomicReference<Map<String, BadgeDefinition>?>(null)

    fun all(): List<BadgeDefinition> = load().values.sortedWith(compareBy({ it.sortOrder }, { it.code }))

    /** 화면에 보일 것만. 숨긴 뱃지는 **지운 것이 아니라** 목록에서 빠질 뿐이다. */
    fun visible(): List<BadgeDefinition> = all().filter { it.visible }

    fun describe(code: String): BadgeInfo {
        val found = load()[code] ?: return BadgeInfo(code, code, "")
        return BadgeInfo(found.code, found.label, found.description)
    }

    fun isVisible(code: String): Boolean = load()[code]?.visible ?: true

    fun sortOrderOf(code: String): Int = load()[code]?.sortOrder ?: Int.MAX_VALUE

    /** 정의 하나. 없으면 코드만 담은 최소 정의를 돌려준다 — 죽지 않는다. */
    fun describeDefinition(code: String): BadgeDefinition =
        load()[code] ?: BadgeDefinition(code, code, "", visible = true, sortOrder = Int.MAX_VALUE, ruleKey = "")

    fun invalidate() = cache.set(null)

    private fun load(): Map<String, BadgeDefinition> =
        cache.get() ?: read().also { cache.set(it) }

    private fun read(): Map<String, BadgeDefinition> =
        jdbcClient.sql("SELECT code, label, description, visible, sort_order, rule_key FROM badges")
            .query { rs, _ ->
                BadgeDefinition(
                    code = rs.getString("code"),
                    label = rs.getString("label"),
                    description = rs.getString("description"),
                    visible = rs.getBoolean("visible"),
                    sortOrder = rs.getInt("sort_order"),
                    ruleKey = rs.getString("rule_key"),
                )
            }
            .list()
            .associateBy { it.code }
}

data class BadgeDefinition(
    val code: String,
    val label: String,
    val description: String,
    val visible: Boolean,
    val sortOrder: Int,
    /**
     * 어느 조건으로 주는가.
     *
     * 지금은 **코드 규칙의 이름**이다. #200 의 DSL 이 오면 이 자리가 규칙으로 대체된다.
     */
    val ruleKey: String,
)
