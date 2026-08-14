package codekr.api.group.controller

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.dto.PageResponse
import codekr.api.config.security.AuthenticatedApi
import codekr.api.group.dto.GroupDetail
import codekr.api.group.dto.GroupInvitePreview
import codekr.api.group.dto.GroupRequest
import codekr.api.group.dto.GroupSummary
import codekr.api.group.dto.OpenGroupSummary
import codekr.api.group.service.GroupMembershipService
import codekr.api.group.service.GroupService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 그룹 (#401, #240 6단계).
 *
 * **전부 로그인이 필요하다.** 초대 링크 미리보기도 마찬가지다 — 눌러서 보고 바로
 * 들어가는 흐름인데, 로그인 없이 보여 주면 그다음 걸음에서 다시 막힌다.
 */
@RestController
@RequestMapping("/api/v1/groups")
class GroupController(
    private val groupService: GroupService,
    private val membershipService: GroupMembershipService,
) {

    @AuthenticatedApi
    @GetMapping
    fun mine(principal: AuthPrincipal): List<GroupSummary> = groupService.mine(principal.userId)

    @AuthenticatedApi
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: GroupRequest, principal: AuthPrincipal): GroupDetail =
        groupService.create(principal.userId, request)

    @AuthenticatedApi
    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long, principal: AuthPrincipal): GroupDetail =
        groupService.detail(id, principal.userId)

    @AuthenticatedApi
    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: GroupRequest,
        principal: AuthPrincipal,
    ): GroupDetail = groupService.update(id, principal.userId, request)

    /** 해산한다. 방장만. 행은 지우지 않는다 (ADR-0007). */
    @AuthenticatedApi
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long, principal: AuthPrincipal) =
        groupService.delete(id, principal.userId)

    /** 초대 링크를 새로 뽑는다. **옛 링크는 그 자리에서 죽는다.** */
    @AuthenticatedApi
    @PostMapping("/{id}/invite")
    fun rotateInvite(@PathVariable id: Long, principal: AuthPrincipal): Map<String, String> =
        mapOf("inviteToken" to membershipService.rotateInvite(id, principal.userId))

    /** 초대 링크가 가리키는 그룹. **가입 전에** 무엇에 들어가는지 보여 준다. */
    @AuthenticatedApi
    @GetMapping("/invites/{token}")
    fun preview(@PathVariable token: String, principal: AuthPrincipal): GroupInvitePreview =
        membershipService.preview(token, principal.userId)

    @AuthenticatedApi
    @PostMapping("/invites/{token}/join")
    fun joinByInvite(@PathVariable token: String, principal: AuthPrincipal): Map<String, Long> =
        mapOf("groupId" to membershipService.joinByInvite(token, principal.userId))

    /**
     * 공개 가입을 켜 둔 그룹을 둘러본다 (#554).
     *
     * **이 조회가 없어서 `openJoin` 이 죽은 설정이었다.** 들어가는 문은 있었는데
     * 그 문이 어디 있는지 찾을 길이 없었다 — 목록은 내 그룹만 줬다.
     */
    @AuthenticatedApi
    @GetMapping("/open")
    fun openGroups(
        @PageableDefault(size = 20) pageable: Pageable,
        principal: AuthPrincipal,
    ): PageResponse<OpenGroupSummary> = groupService.openGroups(principal.userId, pageable)

    /** 공개 가입. 방장이 켜 두었을 때만 열린다. */
    @AuthenticatedApi
    @PostMapping("/{id}/members")
    fun join(@PathVariable id: Long, principal: AuthPrincipal): Map<String, Long> =
        mapOf("groupId" to membershipService.joinOpen(id, principal.userId))

    @AuthenticatedApi
    @DeleteMapping("/{id}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leave(@PathVariable id: Long, principal: AuthPrincipal) =
        membershipService.leave(id, principal.userId)

    @AuthenticatedApi
    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun kick(@PathVariable id: Long, @PathVariable userId: Long, principal: AuthPrincipal) =
        membershipService.kick(id, principal.userId, userId)

    /** 방장을 넘긴다. 넘긴 사람은 그대로 멤버로 남는다. */
    @AuthenticatedApi
    @PostMapping("/{id}/owner/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun transferOwner(@PathVariable id: Long, @PathVariable userId: Long, principal: AuthPrincipal) =
        membershipService.transferOwner(id, principal.userId, userId)
}
