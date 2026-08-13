package codekr.api.auth.password

import codekr.api.config.security.PublicApi
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 비밀번호 재설정 (#315).
 *
 * **둘 다 로그인 없이 부른다.** 비밀번호를 잊은 사람이 쓰는 것이므로 당연하다.
 */
@RestController
@RequestMapping("/api/v1/auth/password")
class PasswordResetController(private val service: PasswordResetService) {

    /**
     * 재설정 메일 요청.
     *
     * **가입 여부와 무관하게 202 다.** 다르게 답하면 어느 주소가 가입되어 있는지
     * 확인하는 도구가 된다. 화면 문구도 "보냈다" 가 아니라 "가입된 주소라면 보냈다" 여야
     * 거짓말이 되지 않는다.
     */
    @PublicApi
    @PostMapping("/reset-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun request(@Valid @RequestBody request: PasswordResetRequest) = service.request(request.email)

    /** 새 비밀번호 정하기. */
    @PublicApi
    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reset(@Valid @RequestBody request: PasswordResetConfirmRequest) =
        service.reset(request.token, request.newPassword)
}

data class PasswordResetRequest(
    @field:Email(message = "이메일 형식이 올바르지 않습니다.")
    @field:NotBlank
    val email: String,
)

data class PasswordResetConfirmRequest(
    @field:NotBlank val token: String,
    // 가입과 같은 규칙이다. 한쪽만 고치면 재설정으로 규칙을 우회할 수 있다.
    @field:Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
    val newPassword: String,
)
