package codekr.api.problem.admin.controller

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.dto.PageResponse
import codekr.api.problem.admin.dto.AdminProblemDetailResponse
import codekr.api.problem.admin.dto.ProblemCreatedResponse
import codekr.api.problem.admin.dto.ProblemUpsertRequest
import codekr.api.problem.admin.dto.VerificationResponse
import codekr.api.problem.admin.service.AdminProblemService
import codekr.api.problem.admin.service.SolutionVerificationService
import codekr.api.problem.dto.ProblemSummaryResponse
import codekr.api.problem.entity.DifficultyTier
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.repository.ProblemSearchCondition
import codekr.api.problem.repository.ProblemSort
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
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

private const val MAX_PAGE_SIZE = 100

/** 접근 제어는 SecurityConfig 의 admin 경로 규칙(hasRole("ADMIN"))이 담당한다. */
@RestController
@RequestMapping("/api/v1/admin/problems")
class AdminProblemController(
    private val adminProblemService: AdminProblemService,
    private val verificationService: SolutionVerificationService,
) {

    @GetMapping
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) category: ProblemCategory?,
        @RequestParam(required = false) tier: DifficultyTier?,
        @RequestParam(defaultValue = "LATEST") sort: ProblemSort,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<ProblemSummaryResponse> {
        // published = null → 미공개 문제까지 포함한다.
        val condition = ProblemSearchCondition(q, category, tier, sort = sort, published = null)
        return adminProblemService.search(condition, PageRequest.of(maxOf(page, 0), size.coerceIn(1, MAX_PAGE_SIZE)))
    }

    @GetMapping("/{id}")
    fun findOne(@PathVariable id: Long): AdminProblemDetailResponse = adminProblemService.findDetail(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: ProblemUpsertRequest,
        principal: AuthPrincipal,
    ): ProblemCreatedResponse = adminProblemService.create(request, principal.userId)

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: ProblemUpsertRequest,
    ): AdminProblemDetailResponse = adminProblemService.update(id, request)

    /**
     * 등록한 정답 코드로 전체 테스트케이스를 검증한다.
     * 진행 상황은 문제 상세의 `verification` 으로 확인한다 (사용자 제출과 같은 채점 큐를 쓴다).
     */
    @PostMapping("/{id}/verify")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun verify(@PathVariable id: Long, principal: AuthPrincipal): VerificationResponse =
        verificationService.verify(id, principal.userId)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = adminProblemService.delete(id)
}
