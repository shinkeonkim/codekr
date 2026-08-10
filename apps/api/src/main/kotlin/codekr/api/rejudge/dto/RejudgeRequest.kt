package codekr.api.rejudge.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 재채점 요청 (#107).
 *
 * 이유가 **필수**다. 결과가 바뀐 사용자에게 그대로 전달되기 때문이다 —
 * "판정이 바뀌었습니다" 만 보내면 우리가 임의로 바꾼 것으로 받아들인다.
 */
data class RejudgeRequest(
    @field:NotBlank(message = "재채점 이유는 필수입니다.")
    @field:Size(max = 200, message = "재채점 이유가 너무 깁니다.")
    val reason: String,
)
