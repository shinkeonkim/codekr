package codekr.api.submission.controller

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.dto.PageResponse
import codekr.api.submission.dto.RunRequest
import codekr.api.submission.dto.RunResponse
import codekr.api.submission.dto.SubmissionDetailResponse
import codekr.api.submission.dto.SubmissionSummaryResponse
import codekr.api.submission.dto.SubmitRequest
import codekr.api.submission.dto.SubmitResponse
import codekr.api.submission.service.SubmissionService
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private const val MAX_PAGE_SIZE = 50

@RestController
@RequestMapping("/api/v1")
class SubmissionController(private val submissionService: SubmissionService) {

    @PostMapping("/problems/{slug}/run")
    fun run(@PathVariable slug: String, @Valid @RequestBody request: RunRequest): RunResponse =
        submissionService.run(slug, request)

    @PostMapping("/problems/{slug}/submissions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun submit(
        @PathVariable slug: String,
        @Valid @RequestBody request: SubmitRequest,
        principal: AuthPrincipal,
    ): SubmitResponse = submissionService.submit(slug, principal.userId, request)

    @GetMapping("/submissions/{id}")
    fun findOne(@PathVariable id: Long, principal: AuthPrincipal): SubmissionDetailResponse =
        submissionService.findDetail(id, principal)

    @GetMapping("/submissions")
    fun findMine(
        @RequestParam(required = false) problemSlug: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        principal: AuthPrincipal,
    ): PageResponse<SubmissionSummaryResponse> = submissionService.findMine(
        userId = principal.userId,
        problemSlug = problemSlug,
        pageable = PageRequest.of(maxOf(page, 0), size.coerceIn(1, MAX_PAGE_SIZE)),
    )
}
