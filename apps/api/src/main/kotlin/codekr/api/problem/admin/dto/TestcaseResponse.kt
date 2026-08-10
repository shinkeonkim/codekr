package codekr.api.problem.admin.dto

import codekr.api.problem.entity.ProblemTestcase
import codekr.api.problem.entity.TestcaseVisibility

data class TestcaseResponse(
    val id: Long,
    val seq: Int,
    val input: String,
    val expectedOutput: String,
    val visibility: TestcaseVisibility,
) {
    companion object {
        fun from(testcase: ProblemTestcase) = TestcaseResponse(
            id = testcase.id,
            seq = testcase.seq,
            input = testcase.input,
            expectedOutput = testcase.expectedOutput,
            visibility = testcase.visibility,
        )
    }
}
