package codekr.api.affiliation.admin

import codekr.api.affiliation.dto.AffiliationRequest
import codekr.api.affiliation.dto.AffiliationResponse
import codekr.api.affiliation.dto.DomainRequest
import codekr.api.affiliation.service.AdminAffiliationService
import codekr.api.auth.security.AuthPrincipal
import codekr.api.config.security.AdminApi
import codekr.api.user.entity.UserRole
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
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
