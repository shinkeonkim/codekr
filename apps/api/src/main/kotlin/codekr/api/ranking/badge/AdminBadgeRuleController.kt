package codekr.api.ranking.badge

import codekr.api.config.security.AdminApi
import codekr.api.user.entity.UserRole
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 뱃지 규칙 편집 (#203).
 *
 * **저장 전에 결과를 볼 수 있어야 한다.** 규칙은 사용자에게 보이는 것을 바꾸는데,
 * 저장한 뒤에야 결과를 알면 되돌릴 방법이 뱃지 회수뿐이다 — 그것은 하지 않기로 했다(#41).
 *
 * **최고 관리자만 만진다.** 뱃지는 모두에게 보이는 것이라 아무 어드민이나 바꾸면 안 된다.
 */
@RestController
@RequestMapping("/api/v1/admin/badge-rules")
class AdminBadgeRuleController(private val service: AdminBadgeRuleService) {

    /**
     * 화면이 쓸 재료 (#203).
     *
     * **화면이 이벤트·지표 목록을 하드코딩하지 않는다** — #200 에서 이벤트가 늘 때마다
     * 화면을 고치는 구조를 만들지 않는다.
     */
    @AdminApi(UserRole.SUPERUSER)
    @GetMapping("/vocabulary")
    fun vocabulary(): BadgeVocabulary = service.vocabulary()

    @AdminApi(UserRole.SUPERUSER)
    @GetMapping
    fun findAll(): List<BadgeRuleResponse> = service.findAll()

    /**
     * 드라이런 — **저장하지 않고** 지금 이 규칙이면 누가 받는지 본다.
     *
     * 전체를 훑는 질의라 **표본만 본다** (상한 있음). "정확히 몇 명" 보다 "말이 되는
     * 규칙인가" 를 확인하는 것이 목적이다.
     */
    @AdminApi(UserRole.SUPERUSER)
    @PostMapping("/dry-run")
    fun dryRun(
        @Valid @RequestBody request: BadgeRuleUpsertRequest,
        /** 이 사람이 받는지 확인한다. 비우면 표본으로 본다. */
        @RequestParam(required = false) userId: Long?,
    ): BadgeDryRunResponse = service.dryRun(request, userId)

    @AdminApi(UserRole.SUPERUSER)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: BadgeRuleUpsertRequest): BadgeRuleResponse =
        service.create(request)

    @AdminApi(UserRole.SUPERUSER)
    /**
     * 고친다. **좁히면 자격을 잃은 사람에게서 뱃지를 거둔다** (#558) — 거두면서 알린다.
     */
    @PutMapping("/{ruleKey}")
    fun update(
        @PathVariable ruleKey: String,
        @Valid @RequestBody request: BadgeRuleUpsertRequest,
    ): BadgeRuleResponse = service.update(ruleKey, request)

    /** 켜고 끈다. **지우는 것보다 끄는 것이 먼저다.** */
    @AdminApi(UserRole.SUPERUSER)
    @PutMapping("/{ruleKey}/enabled")
    fun setEnabled(@PathVariable ruleKey: String, @RequestParam enabled: Boolean): BadgeRuleResponse =
        service.setEnabled(ruleKey, enabled)
}

data class BadgeRuleUpsertRequest(
    @field:NotBlank @field:Size(max = 60) val ruleKey: String,
    @field:NotBlank @field:Size(max = 40) val event: String,
    @field:NotBlank @field:Size(max = 60) val code: String,
    val groupBy: String? = null,
    val conditions: List<BadgeConditionRequest> = emptyList(),
)

data class BadgeConditionRequest(val measure: String, val op: String, val value: Any)

data class BadgeRuleResponse(
    val ruleKey: String,
    val event: String,
    val code: String,
    val groupBy: String?,
    val conditions: List<BadgeConditionRequest>,
    val enabled: Boolean,
)

/** 화면이 고르게 할 목록 (#203). 서버가 내려준다. */
data class BadgeVocabulary(
    val events: List<String>,
    val measures: List<MeasureInfo>,
    val operators: List<String>,
    val groupBys: List<String>,
)

data class MeasureInfo(val name: String, val label: String, val type: String, val events: List<String>)

data class BadgeDryRunResponse(
    /** 문법이 맞는가. 틀리면 [errors] 가 **어디가 틀렸는지** 말한다. */
    val valid: Boolean,
    val errors: List<String>,
    /** 표본에서 조건을 만족한 사람 수와 전체 표본 크기. */
    val matched: Int,
    val sampled: Int,
    /** 지정한 사람이 받는가. 지정하지 않았으면 null. */
    val matchesUser: Boolean?,
    /**
     * 저장하면 **뱃지를 잃을** 사람 수 (#558).
     *
     * [matched] 와 달리 표본이 아니라 **지금 그 뱃지를 가진 사람 전부**를 본다 —
     * 실제로 거둘 대상이므로 어림잡으면 안 된다.
     */
    val losing: Int = 0,
)
