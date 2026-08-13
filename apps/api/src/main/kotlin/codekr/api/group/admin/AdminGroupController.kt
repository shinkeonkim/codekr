package codekr.api.group.admin

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.dto.PageResponse
import codekr.api.config.security.AdminApi
import codekr.api.user.entity.UserRole
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 그룹 관리 (#438).
 *
 * **그룹은 누구나 만들고 이름도 아무거나 쓸 수 있다** (#401). 사칭을 구조적으로 막지
 * 않기로 했으므로, 문제가 되는 것을 **내리는 길**이 있어야 한다 — 지금까지는 방장이
 * 스스로 해산하는 길뿐이었고, 문제가 되는 그룹의 방장이 그럴 이유는 없다.
 *
 * 문제집(#208, #393)과 같은 결이다. 역할도 `ADMIN` 하나로 같다 — 새 역할을 만들지
 * 않는다(#103).
 *
 * **명단은 여기서도 보이지 않는다.** 그룹 안의 일은 그 안의 일이라고 #401 이 정했고,
 * 내릴지 판단하는 데 필요한 것은 이름·방장·인원까지다.
 */
@RestController
@RequestMapping("/api/v1/admin/groups")
@Validated
class AdminGroupController(private val service: AdminGroupService) {

    /** 살아 있는 그룹 목록. 이름으로 좁힐 수 있다. */
    @AdminApi(UserRole.ADMIN)
    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<AdminGroupRow> = service.list(q?.trim(), page, size)

    /**
     * 해산한다. **사유가 필수다** — 멤버 전원에게 그대로 전해진다.
     *
     * 지우지 않고 내린다 (ADR-0007). 그룹 랭킹(#402)이 그 id 를 가리킨다.
     */
    @AdminApi(UserRole.ADMIN)
    @PostMapping("/{id}/takedown")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun takedown(
        @PathVariable id: Long,
        @RequestParam reason: String,
        principal: AuthPrincipal,
    ) = service.takedown(principal.userId, id, reason)
}

/** 목록 한 줄 (#438). 내릴지 판단하는 데 필요한 것만 싣는다. */
data class AdminGroupRow(
    val id: Long,
    val name: String,
    val description: String,
    val ownerNickname: String,
    val memberCount: Int,
    val openJoin: Boolean,
    val createdAt: java.time.Instant,
)
