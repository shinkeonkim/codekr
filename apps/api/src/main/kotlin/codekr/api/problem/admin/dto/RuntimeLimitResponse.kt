package codekr.api.problem.admin.dto

import codekr.api.problem.entity.ProblemRuntimeLimit

data class RuntimeLimitResponse(
    val runtimeId: String,
    val timeLimitMs: Int,
    val memoryLimitMb: Int,
) {
    companion object {
        fun from(limit: ProblemRuntimeLimit) =
            RuntimeLimitResponse(limit.runtimeId, limit.timeLimitMs, limit.memoryLimitMb)
    }
}
