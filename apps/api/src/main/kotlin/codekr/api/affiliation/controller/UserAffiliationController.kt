package codekr.api.affiliation.controller

import codekr.api.affiliation.dto.MyAffiliationsResponse
import codekr.api.affiliation.service.UserAffiliationService
import codekr.api.auth.security.AuthPrincipal
import codekr.api.config.security.AuthenticatedApi
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 내 소속 (#398, #240 3단계).
 *
 * **확인한 주소가 있어야 붙는다.** 그 주소의 도메인이 등록된 소속과 맞아야 하고,
 * 맞지 않으면 붙일 것이 없다 — 확인하지 않으면 `@snu.ac.kr` 이라고 적기만 하면
 * 서울대가 되기 때문이다.
 */
@RestController
@RequestMapping("/api/v1/users/me/affiliations")
class UserAffiliationController(private val service: UserAffiliationService) {

    /** 붙은 것과 **붙일 수 있는 것**을 함께 준다 — 화면이 두 번 묻지 않게. */
    @AuthenticatedApi
    @GetMapping
    fun mine(principal: AuthPrincipal): MyAffiliationsResponse = service.mine(principal.userId)

    @AuthenticatedApi
    @PostMapping("/{affiliationId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun attach(@PathVariable affiliationId: Long, principal: AuthPrincipal) =
        service.attach(principal.userId, affiliationId)

    @AuthenticatedApi
    @DeleteMapping("/{affiliationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun detach(@PathVariable affiliationId: Long, principal: AuthPrincipal) =
        service.detach(principal.userId, affiliationId)
}
