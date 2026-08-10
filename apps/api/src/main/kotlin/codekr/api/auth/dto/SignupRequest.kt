package codekr.api.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:Email(message = "이메일 형식이 올바르지 않습니다.")
    @field:NotBlank(message = "이메일은 필수입니다.")
    val email: String,

    @field:Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
    val password: String,

    @field:Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
    val nickname: String,
)
