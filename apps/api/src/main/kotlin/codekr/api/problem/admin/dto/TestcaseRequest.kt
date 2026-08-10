package codekr.api.problem.admin.dto

import codekr.api.problem.entity.ProblemTestcase
import codekr.api.problem.entity.TestcaseVisibility
import jakarta.validation.constraints.Min

data class TestcaseRequest(
    @field:Min(1) val seq: Int,
    val input: String,
    val expectedOutput: String,
    val visibility: TestcaseVisibility = TestcaseVisibility.HIDDEN,
) {
    fun toEntity() = ProblemTestcase(seq, input, expectedOutput, visibility)
}
