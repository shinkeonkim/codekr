package codekr.api.contest.admin

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.dto.PageResponse
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
class AdminContestController(private val adminContestService: AdminContestService) {

    @GetMapping
    fun findAll(
        @PageableDefault(size = 20) pageable: Pageable,
    ): PageResponse<AdminContestResponse> = adminContestService.findAll(pageable)

    @GetMapping("/{id}")
    fun findDetail(@PathVariable id: Long): AdminContestResponse = adminContestService.findDetail(id)

    @PostMapping
    fun create(
        @RequestBody @Valid request: ContestUpsertRequest,
        principal: AuthPrincipal,
    ): ResponseEntity<AdminContestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(adminContestService.create(request, principal.userId))

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: ContestUpsertRequest,
    ): AdminContestResponse = adminContestService.update(id, request)

    @PutMapping("/{id}/status")
    fun changeStatus(
        @PathVariable id: Long,
        @RequestParam status: ContestStatus,
    ): AdminContestResponse = adminContestService.changeStatus(id, status)

    /** 최종 순위 공개 (#86). 자동이 아니라 사람이 누른다. */
    @PostMapping("/{id}/unfreeze")
    fun unfreeze(@PathVariable id: Long): AdminContestResponse = adminContestService.unfreeze(id)

    @PutMapping("/{id}/problems/{problemId}/exclusion")
    fun excludeProblem(
        @PathVariable id: Long,
        @PathVariable problemId: Long,
        @RequestParam excluded: Boolean,
    ): AdminContestResponse = adminContestService.excludeProblem(id, problemId, excluded)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = adminContestService.delete(id)
}
