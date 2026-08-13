package codekr.api.problem.admin.dto

import codekr.api.problem.entity.ProblemTestcase
import codekr.api.problem.entity.TestcaseVisibility
import jakarta.validation.constraints.Min

data class TestcaseRequest(
    @field:Min(1) val seq: Int,
    val input: String,
    val expectedOutput: String,
    val visibility: TestcaseVisibility = TestcaseVisibility.HIDDEN,
    /** 부분 점수 묶음 (#473). 비면 묶음이 없는 문제다. */
    val groupNo: Int? = null,
) {
    fun toEntity() = ProblemTestcase(seq, input, expectedOutput, visibility, groupNo)
}
