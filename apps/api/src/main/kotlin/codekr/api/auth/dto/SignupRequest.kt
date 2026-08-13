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

    /**
     * 주소가 되는 이름 (#307). **비우면 닉네임에서 만든다.**
     *
     * 가입 화면에서 두 칸을 받으면 처음 오는 사람에게 설명할 것이 하나 는다 —
     * 대신 규칙에 맞지 않으면 서버가 만들어 준다. 나중에 바꿀 수 없으므로
     * **직접 정하고 싶은 사람만** 적는다.
     */
    val handle: String? = null,

    /**
     * 동의한 약관 판들 (#235).
     *
     * **필수를 다 받지 못하면 가입이 되지 않는다.** 동의 없이 만들어진 계정은 나중에
     * "받았다" 고 말할 근거가 없다.
     */
    val agreedTermIds: List<Long> = emptyList(),
)
