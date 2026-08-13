package codekr.api.queue.message

import codekr.api.problem.entity.ProblemTestcase

data class JudgeTestcaseMessage(
    val id: Long,
    val seq: Int,
    val input: String,
    val expectedOutput: String,
    /** 부분 점수 묶음 (#473). 없으면 0 — 채점기가 그것을 "묶음 없음" 으로 읽는다. */
    val groupNo: Int = 0,
) {
    companion object {
        fun from(testcase: ProblemTestcase) = JudgeTestcaseMessage(
            testcase.id,
            testcase.seq,
            testcase.input,
            testcase.expectedOutput,
            testcase.groupNo ?: 0,
        )
    }
}
