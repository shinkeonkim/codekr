package codekr.api.contest.admin

import codekr.api.user.entity.UserRole
import codekr.api.config.security.AdminApi
import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.dto.PageResponse
import codekr.api.contest.audit.ContestAuditService
import codekr.api.contest.audit.SharedAddress
import codekr.api.contest.entity.ContestStatus
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
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
 * 대회 운영 (#61).
 *
 * 인가는 `SecurityConfig` 의 경로 규칙이 맡는다 — `CONTEST_MANAGER` 이상.
 * 자원 범위(어느 대회의 관리자인가)는 아직 없다. 전역 역할만 본다 (#103).
 */
@RestController
@RequestMapping("/api/v1/admin/contests")
class AdminContestController(
    private val adminContestService: AdminContestService,
    private val auditService: ContestAuditService,
) {

    @AdminApi(UserRole.CONTEST_MANAGER)
    @GetMapping
    fun findAll(
        @PageableDefault(size = 20) pageable: Pageable,
    ): PageResponse<AdminContestResponse> = adminContestService.findAll(pageable)

    @AdminApi(UserRole.CONTEST_MANAGER)
    @GetMapping("/{id}")
    fun findDetail(@PathVariable id: Long): AdminContestResponse = adminContestService.findDetail(id)

    @AdminApi(UserRole.CONTEST_MANAGER)
    @PostMapping
    fun create(
        @RequestBody @Valid request: ContestUpsertRequest,
        principal: AuthPrincipal,
    ): ResponseEntity<AdminContestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(adminContestService.create(request, principal.userId))

    @AdminApi(UserRole.CONTEST_MANAGER)
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: ContestUpsertRequest,
    ): AdminContestResponse = adminContestService.update(id, request)

    @AdminApi(UserRole.CONTEST_MANAGER)
    @PutMapping("/{id}/status")
    fun changeStatus(
        @PathVariable id: Long,
        @RequestParam status: ContestStatus,
    ): AdminContestResponse = adminContestService.changeStatus(id, status)

    /** 최종 순위 공개 (#86). 자동이 아니라 사람이 누른다. */
    @AdminApi(UserRole.CONTEST_MANAGER)
    @PostMapping("/{id}/unfreeze")
    fun unfreeze(@PathVariable id: Long): AdminContestResponse = adminContestService.unfreeze(id)

    @AdminApi(UserRole.CONTEST_MANAGER)
    @PutMapping("/{id}/problems/{problemId}/exclusion")
    fun excludeProblem(
        @PathVariable id: Long,
        @PathVariable problemId: Long,
        @RequestParam excluded: Boolean,
    ): AdminContestResponse = adminContestService.excludeProblem(id, problemId, excluded)

    /**
     * 같은 주소에서 제출한 계정들 (#148).
     *
     * **전체 목록을 내리지 않는다.** 운영자가 이유 없이 참가자의 IP 를 훑을 수 있게 되면
     * 그것은 감사가 아니라 감시다. 계정이 둘 이상 겹치는 주소만 보여준다.
     */
    @AdminApi(UserRole.CONTEST_MANAGER)
    @GetMapping("/{id}/audit/shared-addresses")
    fun sharedAddresses(@PathVariable id: Long): List<SharedAddress> =
        auditService.sharedAddresses(id)

    @AdminApi(UserRole.CONTEST_MANAGER)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = adminContestService.delete(id)
}
