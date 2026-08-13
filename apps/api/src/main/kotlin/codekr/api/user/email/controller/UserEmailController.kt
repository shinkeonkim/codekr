package codekr.api.user.email.controller

import codekr.api.user.email.dto.AddEmailRequest
import codekr.api.user.email.dto.UserEmailResponse
import codekr.api.user.email.service.UserEmailService
import codekr.api.auth.security.AuthPrincipal
import codekr.api.config.security.AuthenticatedApi
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
 * 확인한 메일 주소 여러 개 (#396, #240 1단계).
 *
 * **소속 인증이 여기에 통째로 기댄다.** 학교·회사 메일을 확인해야 소속이 붙는데,
 * 로그인 주소를 그것으로 바꾸게 하면 **졸업하는 순간 로그인을 잃는다.**
 */
@RestController
@RequestMapping("/api/v1/users/me/emails")
class UserEmailController(private val service: UserEmailService) {

    @AuthenticatedApi
    @GetMapping
    fun list(principal: AuthPrincipal): List<UserEmailResponse> = service.list(principal.userId)

    /**
     * 주소를 더한다 — **바로 붙지 않고 확인 메일이 간다.**
     *
     * 확인하지 않으면 `@snu.ac.kr` 이라고 적기만 하면 서울대가 된다 (#240 이 그것을
     * 이 이슈의 선행으로 둔 이유다).
     */
    @AuthenticatedApi
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun add(@Valid @RequestBody request: AddEmailRequest, principal: AuthPrincipal) =
        service.requestVerification(principal.userId, request.email.trim().lowercase())

    @AuthenticatedApi
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(@PathVariable id: Long, principal: AuthPrincipal) = service.remove(principal.userId, id)
}
