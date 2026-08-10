package codekr.api.submission.dto

import codekr.api.submission.entity.SubmissionVisibility
import jakarta.validation.constraints.NotBlank

data class SubmitRequest(
    @field:NotBlank(message = "실행 환경은 필수입니다.")
    val runtimeId: String,

    @field:NotBlank(message = "소스 코드는 비어 있을 수 없습니다.")
    val sourceCode: String,

    /** 소스 코드 공개 범위. 지정하지 않으면 비공개다. */
    val visibility: SubmissionVisibility = SubmissionVisibility.PRIVATE,
)
