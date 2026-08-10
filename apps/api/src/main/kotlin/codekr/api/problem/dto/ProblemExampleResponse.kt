package codekr.api.problem.dto

import codekr.api.problem.entity.ProblemTestcase

data class ProblemExampleResponse(val seq: Int, val input: String, val output: String) {
    companion object {
        fun from(testcase: ProblemTestcase) =
            ProblemExampleResponse(testcase.seq, testcase.input, testcase.expectedOutput)
    }
}
