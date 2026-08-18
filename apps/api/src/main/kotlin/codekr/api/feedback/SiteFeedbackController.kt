package codekr.api.feedback

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.dto.PageResponse
import codekr.api.config.security.AdminApi
import codekr.api.config.security.PublicApi
import codekr.api.config.security.AuthenticatedApi
import codekr.api.user.entity.UserRole
import org.springframework.data.domain.PageRequest
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 사이트 신고·제안 (#603).
 *
 * **로그인한 사람만 넣는다.** 막을 것(속도 제한·캡차)이 없는 채로 열면 스팸이 그대로
 * 쌓이고, 처리 결과를 되돌려 줄 곳도 없다. 가입 자체가 안 되는 사람은 푸터의 저장소
 * 링크가 남는다 — 그 경우가 유일하게 열어 둘 값어치가 있는 쪽이라, 별도 이슈로 뗐다.
 */
@RestController
class SiteFeedbackController(private val feedbackService: SiteFeedbackService) {

    /**
     * 로그인하지 못하는 사람의 통로 (#611).
     *
     * **경로를 나눈 이유**: 같은 경로를 열면 로그인한 사람의 신고도 익명으로 들어올 수
     * 있고, 그때 "누가 넣었나" 가 조용히 비어 버린다. 길이 둘이면 무엇으로 들어왔는지가
     * 저장된 값에 그대로 남는다.
     *
     * **답을 돌려주지 않는다.** 답하려면 연락처를 받아야 하는데, 그것은 가입 없이
     * 개인정보를 모으는 일이라 약관(#235)이 다루지 않는 자리가 된다.
     */
    @PublicApi
    @PostMapping("/api/v1/feedbacks/anonymous")
    @ResponseStatus(HttpStatus.CREATED)
    fun submitAnonymously(
        @RequestBody request: SiteFeedbackRequest,
        httpRequest: HttpServletRequest,
    ): SiteFeedbackResponse = feedbackService.submitAnonymously(
        kind = request.kind,
        body = request.body,
        pageUrl = request.pageUrl,
        // 로그인이 없으므로 주소로 센다 (#475 의 배지가 같은 방법을 쓴다).
        clientKey = httpRequest.remoteAddr ?: "unknown",
    )

    @AuthenticatedApi
    @PostMapping("/api/v1/feedbacks")
    @ResponseStatus(HttpStatus.CREATED)
    fun submit(
        @RequestBody request: SiteFeedbackRequest,
        principal: AuthPrincipal,
    ): SiteFeedbackResponse = feedbackService.submit(
        reporterId = principal.userId,
        kind = request.kind,
        body = request.body,
        pageUrl = request.pageUrl,
    )

    /** 내가 넣은 것. **어디로 갔는지 볼 수 있어야 다시 넣는다.** */
    @AuthenticatedApi
    @GetMapping("/api/v1/feedbacks/me")
    fun listMine(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        principal: AuthPrincipal,
    ): PageResponse<SiteFeedbackResponse> = PageResponse.from(
        feedbackService.listMine(principal.userId, PageRequest.of(maxOf(page, 0), size.coerceIn(1, 100))),
    )

    @AdminApi(UserRole.ADMIN)
    @GetMapping("/api/v1/admin/feedbacks")
    fun list(
        @RequestParam(required = false) status: FeedbackStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<SiteFeedbackResponse> = PageResponse.from(
        feedbackService.list(status, PageRequest.of(maxOf(page, 0), size.coerceIn(1, 100))),
    )

    @AdminApi(UserRole.ADMIN)
    @PostMapping("/api/v1/admin/feedbacks/{id}/resolution")
    fun resolve(
        @PathVariable id: Long,
        @RequestBody request: ResolveFeedbackRequest,
        principal: AuthPrincipal,
    ): SiteFeedbackResponse = feedbackService.resolve(id, principal.userId, request.status, request.resolution)
}

data class SiteFeedbackRequest(val kind: FeedbackKind, val body: String, val pageUrl: String? = null)

data class ResolveFeedbackRequest(val status: FeedbackStatus, val resolution: String? = null)
