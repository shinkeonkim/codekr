package codekr.api.queue.message

import codekr.api.problem.entity.ProblemTestcase

data class JudgeTestcaseMessage(
    val id: Long,
    val seq: Int,
    val input: String,
    val expectedOutput: String,
) {
    companion object {
        fun from(testcase: ProblemTestcase) =
            JudgeTestcaseMessage(testcase.id, testcase.seq, testcase.input, testcase.expectedOutput)
    }
}
