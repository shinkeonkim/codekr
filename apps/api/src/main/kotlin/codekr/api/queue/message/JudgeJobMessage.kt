package codekr.api.queue.message

import codekr.api.problem.entity.Problem
import codekr.api.submission.entity.Submission

data class JudgeJobMessage(
    val submissionId: Long,
    val problemId: Long,
    val runtimeId: String,
    val sourceCode: String,
    val timeLimitMs: Int,
    val memoryLimitMb: Int,
    val testcases: List<JudgeTestcaseMessage>,
) {
    companion object {
        /** 채점기가 DB 를 읽지 않도록 채점에 필요한 값을 모두 실어 보낸다 (ADR-0004). */
        fun of(submission: Submission, problem: Problem) = JudgeJobMessage(
            submissionId = submission.id,
            problemId = problem.id,
            runtimeId = submission.runtimeId,
            sourceCode = submission.sourceCode,
            timeLimitMs = problem.timeLimitMs,
            memoryLimitMb = problem.memoryLimitMb,
            testcases = problem.testcases.map(JudgeTestcaseMessage::from),
        )
    }
}
