package codekr.api.user.email.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class AddEmailRequest(
    @field:NotBlank(message = "메일 주소를 입력해 주세요.")
    @field:Email(message = "메일 주소 형식이 아닙니다.")
    val email: String = "",
)

data class UserEmailResponse(val id: Long, val email: String, val verifiedAt: Instant)
