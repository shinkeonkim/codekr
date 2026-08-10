package codekr.api.submission.dto

import codekr.api.submission.entity.SubmissionVisibility
import jakarta.validation.constraints.NotBlank

data class SubmitRequest(
    @field:NotBlank(message = "실행 환경은 필수입니다.")
    val runtimeId: String,

    @field:NotBlank(message = "소스 코드는 비어 있을 수 없습니다.")
    val sourceCode: String,

    /**
     * 소스 코드 공개 범위. **지정하지 않으면 사용자 기본값을 쓴다** (#104).
     *
     * null 을 기본값으로 둔 이유: PRIVATE 을 기본값으로 두면 "명시적으로 비공개를 골랐다"
     * 와 "아무것도 안 골랐다" 를 서버가 구분할 수 없다. 그러면 사용자 기본값을 적용할
     * 수 없다.
     */
    val visibility: SubmissionVisibility? = null,
)
