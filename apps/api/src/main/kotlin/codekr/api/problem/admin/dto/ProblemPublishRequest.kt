package codekr.api.problem.admin.dto

import jakarta.validation.constraints.NotEmpty

/**
 * 여러 문제의 공개 여부를 한 번에 (#627).
 *
 * **`published` 를 몸통에 받는다.** 경로를 `/publish`·`/unpublish` 둘로 가르면 같은
 * 일을 하는 코드가 둘이 되고, 화면에서도 버튼마다 다른 함수를 부르게 된다.
 */
data class ProblemPublishRequest(
    @field:NotEmpty(message = "바꿀 문제를 고르지 않았습니다.")
    val ids: List<Long> = emptyList(),
    val published: Boolean = false,
)
