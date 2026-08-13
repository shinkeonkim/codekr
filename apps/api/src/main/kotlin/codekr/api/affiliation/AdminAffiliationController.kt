package codekr.api.affiliation

import codekr.api.audit.entity.AdminAction
import codekr.api.audit.service.AdminAuditService
import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.security.AdminApi
import codekr.api.user.entity.UserRole
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 소속과 도메인 관리 (#397, #240 2단계).
 *
 * **목록이 있어야 인증이 붙는다.** 사용자가 학교 메일을 확인하면(#396) 그 도메인이
 * 어느 소속인지 여기서 찾는다.
 *
 * `ADMIN` 이다 — 기획서가 "어드민이 관리한다" 로 정했고, 게시판·문제집 관리와 같은 결이다.
 * 다만 **잘못 넣으면 그 도메인을 가진 모두가 그 소속을 얻는다.** 그래서 관리 기록에 남긴다.
 */
@RestController
@RequestMapping("/api/v1/admin/affiliations")
class AdminAffiliationController(private val service: AdminAffiliationService) {

    @AdminApi(UserRole.ADMIN)
    @GetMapping
    fun list(): List<AffiliationResponse> = service.list()

    @AdminApi(UserRole.ADMIN)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: AffiliationRequest, principal: AuthPrincipal) =
        service.create(principal.userId, request)

    @AdminApi(UserRole.ADMIN)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long, principal: AuthPrincipal) = service.delete(principal.userId, id)

    @AdminApi(UserRole.ADMIN)
    @PostMapping("/{id}/domains")
    @ResponseStatus(HttpStatus.CREATED)
    fun addDomain(
        @PathVariable id: Long,
        @Valid @RequestBody request: DomainRequest,
        principal: AuthPrincipal,
    ) = service.addDomain(principal.userId, id, request.domain)

    @AdminApi(UserRole.ADMIN)
    @DeleteMapping("/{id}/domains/{domainId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeDomain(@PathVariable id: Long, @PathVariable domainId: Long, principal: AuthPrincipal) =
        service.removeDomain(principal.userId, id, domainId)
}

data class AffiliationRequest(
    @field:NotBlank(message = "이름을 입력해 주세요.")
    @field:Size(max = 100)
    val name: String = "",
    val kind: AffiliationKind = AffiliationKind.SCHOOL,
)

data class DomainRequest(
    @field:NotBlank(message = "도메인을 입력해 주세요.")
    val domain: String = "",
)

data class AffiliationResponse(
    val id: Long,
    val name: String,
    val kind: AffiliationKind,
    val kindLabel: String,
    val domains: List<DomainResponse>,
)

data class DomainResponse(val id: Long, val domain: String)

@Service
class AdminAffiliationService(
    private val affiliations: AffiliationRepository,
    private val domains: AffiliationDomainRepository,
    private val auditService: AdminAuditService,
) {

    fun list(): List<AffiliationResponse> =
        affiliations.findByDeletedAtIsNullOrderByNameAsc().map { affiliation ->
            AffiliationResponse(
                id = affiliation.id,
                name = affiliation.name,
                kind = affiliation.kind,
                kindLabel = affiliation.kind.label,
                domains = domains.findByAffiliationIdOrderByDomainAsc(affiliation.id)
                    .map { DomainResponse(it.id, it.domain) },
            )
        }

    @Transactional
    fun create(actorId: Long, request: AffiliationRequest): AffiliationResponse {
        val name = request.name.trim()
        // 같은 이름이 둘이면 사람이 어느 것을 고를지 알 수 없다.
        if (affiliations.existsByNameAndDeletedAtIsNull(name)) throw ApiException(ErrorCode.NICKNAME_ALREADY_EXISTS)

        val saved = affiliations.save(Affiliation(name, request.kind))
        record(actorId, "소속 추가", "${saved.name} (${saved.kind.label})")
        return AffiliationResponse(saved.id, saved.name, saved.kind, saved.kind.label, emptyList())
    }

    /**
     * 소속을 내린다.
     *
     * **행을 지우지 않는다** (ADR-0007). 이미 이 소속이 붙은 사람들이 있고, 지우면
     * 그들의 소속이 무엇이었는지 아무도 모르게 된다.
     *
     * **도메인은 함께 뗀다.** 남겨 두면 지운 소속에 새 사람이 계속 붙는다.
     */
    @Transactional
    fun delete(actorId: Long, id: Long) {
        val affiliation = affiliations.findByIdAndDeletedAtIsNull(id)
            ?: throw ApiException(ErrorCode.USER_NOT_FOUND)
        domains.deleteByAffiliationId(id)
        affiliation.delete()
        record(actorId, "소속 내림", affiliation.name)
    }

    /**
     * 도메인을 붙인다.
     *
     * **소문자로 저장하고 `@` 를 걷어 낸다.** 사람은 `@snu.ac.kr` 이라고 적는 일이
     * 잦은데, 그대로 두면 메일 주소의 도메인과 영영 안 맞는다.
     */
    @Transactional
    fun addDomain(actorId: Long, affiliationId: Long, raw: String): DomainResponse {
        val affiliation = affiliations.findByIdAndDeletedAtIsNull(affiliationId)
            ?: throw ApiException(ErrorCode.USER_NOT_FOUND)
        val domain = normalize(raw)

        // **한 도메인은 한 소속에만 붙는다.** 둘에 붙으면 같은 메일로 두 소속을 얻는다.
        domains.findByDomain(domain)?.let { throw ApiException(ErrorCode.EMAIL_ALREADY_EXISTS) }

        val saved = domains.save(AffiliationDomain(affiliationId, domain))
        record(actorId, "도메인 추가", "${affiliation.name} ← $domain")
        return DomainResponse(saved.id, saved.domain)
    }

    @Transactional
    fun removeDomain(actorId: Long, affiliationId: Long, domainId: Long) {
        val domain = domains.findById(domainId).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }
        if (domain.affiliationId != affiliationId) throw ApiException(ErrorCode.FORBIDDEN)
        domains.delete(domain)
        record(actorId, "도메인 제거", domain.domain)
    }

    /**
     * 도메인 형식.
     *
     * **완벽한 검사를 하지 않는다.** 도메인 규칙을 정규식으로 다 적으려 들면 맞는 것을
     * 막는 날이 온다. 여기서 막는 것은 **사람이 흔히 하는 실수** — `@` 를 붙이거나,
     * 메일 주소를 통째로 넣거나, 점이 없는 값이다.
     */
    private fun normalize(raw: String): String {
        val domain = raw.trim().lowercase().removePrefix("@").substringAfterLast('@')
        if (!domain.contains('.') || domain.startsWith('.') || domain.endsWith('.') || domain.contains(' ')) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "도메인 형식이 아닙니다: $raw")
        }
        return domain
    }

    /** **잘못 넣으면 그 도메인을 가진 모두가 그 소속을 얻는다** — 누가 무엇을 했는지 남긴다 (#225). */
    private fun record(actorId: Long, what: String, detail: String) {
        auditService.record(
            actorId = actorId,
            action = AdminAction.AFFILIATION_CHANGE,
            targetId = 0,
            targetLabel = what,
            detail = detail,
        )
    }
}
