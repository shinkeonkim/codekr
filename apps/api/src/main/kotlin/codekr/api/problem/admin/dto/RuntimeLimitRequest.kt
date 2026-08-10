package codekr.api.problem.admin.dto

import codekr.api.problem.entity.ExecutionLimits
import codekr.api.problem.entity.ProblemRuntimeLimit
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/**
 * 특정 런타임에만 적용할 실행 제한 (#97).
 *
 * 문제 기본 제한과 **같은 허용 범위**를 받는다. 오버라이드라고 해서 범위를 벗어날 수 있으면
 * 실행기가 거부하는 값을 어드민이 저장할 수 있게 된다 (docs/06).
 */
data class RuntimeLimitRequest(
    @field:NotBlank(message = "실행 환경은 필수입니다.")
    val runtimeId: String,

    @field:Min(value = ExecutionLimits.MIN_TIME_LIMIT_MS.toLong(), message = "시간 제한이 너무 짧습니다.")
    @field:Max(value = ExecutionLimits.MAX_TIME_LIMIT_MS.toLong(), message = "시간 제한이 너무 깁니다.")
    val timeLimitMs: Int,

    @field:Min(value = ExecutionLimits.MIN_MEMORY_LIMIT_MB.toLong(), message = "메모리 제한이 너무 작습니다.")
    @field:Max(value = ExecutionLimits.MAX_MEMORY_LIMIT_MB.toLong(), message = "메모리 제한이 너무 큽니다.")
    val memoryLimitMb: Int,
) {
    fun toEntity() = ProblemRuntimeLimit(runtimeId, timeLimitMs, memoryLimitMb)
}
