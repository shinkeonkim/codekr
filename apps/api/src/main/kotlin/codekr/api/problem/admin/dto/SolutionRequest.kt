package codekr.api.problem.admin.dto

import jakarta.validation.constraints.NotBlank

/**
 * 문제의 정답 코드. 선택 사항이며, 넣으면 전체 테스트케이스를 검증할 수 있다.
 *
 * 이 값은 어드민 응답에만 실린다 — 공개 DTO 에는 필드 자체가 없다.
 */
data class SolutionRequest(
    @field:NotBlank(message = "정답 코드의 실행 환경은 필수입니다.")
    val runtimeId: String,

    @field:NotBlank(message = "정답 코드는 비어 있을 수 없습니다.")
    val sourceCode: String,
)
