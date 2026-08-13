package codekr.api.auth.email

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.security.AuthenticatedApi
import codekr.api.config.security.PublicApi
import codekr.api.user.repository.UserRepository
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 이메일 확인 (#233). */
@RestController
@RequestMapping("/api/v1/auth/email")
class EmailVerificationController(
    private val service: EmailVerificationService,
    private val userRepository: UserRepository,
) {

    /**
     * 링크를 눌러 확인한다.
     *
     * **로그인이 필요 없다.** 메일을 받은 기기와 로그인한 기기가 다를 수 있고,
     * 토큰 자체가 본인 확인 수단이다.
     */
    @PublicApi
    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun verify(@RequestBody request: VerifyEmailRequest) = service.verify(request.token)

    /** 메일이 안 왔을 때 다시 받는다. 쿨다운과 하루 상한이 걸린다. */
    @AuthenticatedApi
    @PostMapping("/verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun resend(principal: AuthPrincipal) {
        val user = userRepository.findById(principal.userId)
            .orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }
        if (user.emailVerifiedAt != null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이미 확인된 주소입니다.")
        }
        service.send(user.id, user.email, enforceCooldown = true)
    }
}

data class VerifyEmailRequest(@field:NotBlank val token: String)
