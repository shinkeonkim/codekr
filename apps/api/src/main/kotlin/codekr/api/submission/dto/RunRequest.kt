package codekr.api.submission.dto

import jakarta.validation.constraints.NotBlank

data class RunRequest(
    @field:NotBlank(message = "실행 환경은 필수입니다.")
    val runtimeId: String,

    @field:NotBlank(message = "소스 코드는 비어 있을 수 없습니다.")
    val sourceCode: String,

    val stdin: String = "",
)
