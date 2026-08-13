package codekr.api.terms

import codekr.api.auth.security.AuthPrincipal
import codekr.api.config.security.AuthenticatedApi
import codekr.api.config.security.PublicApi
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/** 약관 (#235). */
@RestController
@RequestMapping("/api/v1/terms")
class TermsController(private val service: TermsService) {

    /** 지금 시행 중인 약관들. **가입 화면이 이것으로 동의 항목을 만든다.** */
    @PublicApi
    @GetMapping
    fun effective(): List<TermSummaryResponse> = service.effective().map(TermSummaryResponse::of)

    /** 전문. 로그인 없이 읽는다 — 가입하기 전에 읽어야 하는 문서다. */
    @PublicApi
    @GetMapping("/{id}")
    fun findOne(@PathVariable id: Long): TermDetailResponse = TermDetailResponse.of(service.findOne(id))

    /** 지금 다시 받아야 하는 것들 (#235). 없으면 빈 목록이다. */
    @AuthenticatedApi
    @GetMapping("/pending")
    fun pending(principal: AuthPrincipal): List<TermSummaryResponse> =
        service.pending(principal.userId).map(TermSummaryResponse::of)

    /** 내가 동의한 내역 — 설정 화면이 보여 준다 (#104). */
    @AuthenticatedApi
    @GetMapping("/agreements")
    fun mine(principal: AuthPrincipal): List<TermAgreementResponse> =
        service.mine(principal.userId).map { (document, agreedAt) ->
            TermAgreementResponse(document.id, document.kind, document.title, document.version, agreedAt)
        }

    /** 개정 뒤에 다시 받는다. */
    @AuthenticatedApi
    @PostMapping("/agreements")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun agree(@RequestBody request: TermAgreeRequest, principal: AuthPrincipal) =
        service.agree(principal.userId, request.documentIds)
}

data class TermAgreeRequest(val documentIds: List<Long> = emptyList())

data class TermSummaryResponse(
    val id: Long,
    val kind: TermKind,
    val kindLabel: String,
    val title: String,
    val version: String,
    val required: Boolean,
    val effectiveAt: Instant,
) {
    companion object {
        fun of(document: TermDocument) = TermSummaryResponse(
            document.id,
            document.kind,
            document.kind.label,
            document.title,
            document.version,
            document.required,
            document.effectiveAt,
        )
    }
}

data class TermDetailResponse(
    val id: Long,
    val kind: TermKind,
    val title: String,
    val version: String,
    val body: String,
    val effectiveAt: Instant,
) {
    companion object {
        fun of(document: TermDocument) = TermDetailResponse(
            document.id,
            document.kind,
            document.title,
            document.version,
            document.body,
            document.effectiveAt,
        )
    }
}

data class TermAgreementResponse(
    val documentId: Long,
    val kind: TermKind,
    val title: String,
    val version: String,
    val agreedAt: Instant,
)
