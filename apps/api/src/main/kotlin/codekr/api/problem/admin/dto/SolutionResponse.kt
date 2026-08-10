package codekr.api.problem.admin.dto

import codekr.api.problem.entity.Problem

data class SolutionResponse(val runtimeId: String, val sourceCode: String) {
    companion object {
        fun from(problem: Problem): SolutionResponse? {
            val runtimeId = problem.solutionRuntimeId ?: return null
            val sourceCode = problem.solutionSourceCode ?: return null
            return SolutionResponse(runtimeId, sourceCode)
        }
    }
}
