package codekr.api.problem.report

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.dto.PageResponse
import codekr.api.config.security.AdminApi
import codekr.api.config.security.AuthenticatedApi
import codekr.api.problem.service.ProblemService
import codekr.api.user.entity.UserRole
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 문제 오류 신고 (#478).
 *
 * **질문(#139)과 자리를 가른다.** 질문은 다른 사용자가 답하고, 신고는 어드민만 처리한다 —
 * 섞이면 질문 백 개 사이에 신고 하나가 묻힌다.
 */
@RestController
class ProblemReportController(
    private val reportService: ProblemReportService,
    private val problemService: ProblemService,
) {

    @AuthenticatedApi
    @PostMapping("/api/v1/problems/{slug}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    fun report(
        @PathVariable slug: String,
        @RequestBody request: ProblemReportRequest,
        principal: AuthPrincipal,
    ): ProblemReportResponse = reportService.report(
        problemId = problemService.requirePublished(slug).id,
        reporterId = principal.userId,
        kind = request.kind,
        body = request.body,
    )

    @AdminApi(UserRole.PROBLEM_SETTER)
    @GetMapping("/api/v1/admin/problem-reports")
    fun list(
        @RequestParam(required = false) status: ReportStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<ProblemReportResponse> =
        PageResponse.from(reportService.list(status, PageRequest.of(page, size.coerceIn(1, 100))))

    @AdminApi(UserRole.PROBLEM_SETTER)
    @PostMapping("/api/v1/admin/problem-reports/{id}/resolution")
    fun resolve(
        @PathVariable id: Long,
        @RequestBody request: ResolveReportRequest,
        principal: AuthPrincipal,
    ): ProblemReportResponse =
        reportService.resolve(id, principal.userId, request.status, request.resolution)
}

data class ProblemReportRequest(val kind: ReportKind, val body: String)

data class ResolveReportRequest(val status: ReportStatus, val resolution: String? = null)
