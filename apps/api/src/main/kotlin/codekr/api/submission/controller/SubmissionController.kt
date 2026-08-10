package codekr.api.submission.controller

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.dto.PageResponse
import codekr.api.submission.dto.RunRequest
import codekr.api.submission.dto.RunResponse
import codekr.api.submission.dto.SubmissionDetailResponse
import codekr.api.submission.dto.SubmissionSummaryResponse
import codekr.api.submission.dto.SubmitRequest
import codekr.api.submission.dto.SubmitResponse
import codekr.api.submission.dto.VisibilityChangeRequest
import codekr.api.submission.entity.Verdict
import codekr.api.submission.repository.SubmissionSearchCondition
import codekr.api.submission.repository.SubmissionSort
import codekr.api.submission.service.SubmissionService
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId

private const val MAX_PAGE_SIZE = 50

/** 날짜 필터의 하루 경계를 정하는 기준 시간대 (docs/03). */
private val SERVICE_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

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

    @PatchMapping("/submissions/{id}/visibility")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changeVisibility(
        @PathVariable id: Long,
        @Valid @RequestBody request: VisibilityChangeRequest,
        principal: AuthPrincipal,
    ) = submissionService.changeVisibility(id, principal, request)

    /**
     * 전체 회원의 제출 목록. 소스 코드는 담기지 않으며 공개 범위는 상세에서 적용된다 (#33).
     */
    @GetMapping("/submissions/explore")
    fun explore(
        @RequestParam(required = false) problemSlug: String?,
        @RequestParam(required = false) nickname: String?,
        @RequestParam(required = false) runtimeId: String?,
        @RequestParam(required = false) verdict: Verdict?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(defaultValue = "LATEST") sort: SubmissionSort,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        principal: AuthPrincipal,
    ): PageResponse<SubmissionSummaryResponse> {
        val condition = SubmissionSearchCondition(
            problemSlug = problemSlug,
            nickname = nickname,
            runtimeId = runtimeId,
            verdict = verdict,
            submittedFrom = from?.atStartOfDay(SERVICE_ZONE)?.toInstant(),
            // 종료일은 그날 전체를 포함해야 하므로 다음 날 0시 미만으로 본다.
            submittedTo = to?.plusDays(1)?.atStartOfDay(SERVICE_ZONE)?.toInstant(),
            sort = sort,
        )
        return submissionService.search(
            condition,
            PageRequest.of(maxOf(page, 0), size.coerceIn(1, MAX_PAGE_SIZE)),
            principal,
        )
    }

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
