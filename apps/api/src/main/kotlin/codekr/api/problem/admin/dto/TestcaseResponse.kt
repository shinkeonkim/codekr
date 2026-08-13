package codekr.api.problem.admin.dto

import codekr.api.problem.entity.ProblemTestcase
import codekr.api.problem.entity.TestcaseVisibility

data class TestcaseResponse(
    val id: Long,
    val seq: Int,
    val input: String,
    val expectedOutput: String,
    val visibility: TestcaseVisibility,
    /** 부분 점수 묶음 (#473). null 이면 묶음이 없는 문제다. */
    val groupNo: Int? = null,
) {
    companion object {
        fun from(testcase: ProblemTestcase) = TestcaseResponse(
            id = testcase.id,
            seq = testcase.seq,
            input = testcase.input,
            expectedOutput = testcase.expectedOutput,
            visibility = testcase.visibility,
            groupNo = testcase.groupNo,
        )
    }
}
