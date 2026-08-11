package codekr.api.queue.message

import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemKind
import codekr.api.submission.entity.Submission

data class JudgeJobMessage(
    val submissionId: Long,
    val problemId: Long,
    /**
     * 채점 방식 (#59). 채점기가 어느 구현으로 보낼지 이 값으로 고른다.
     *
     * **없으면 JUDGE_STDIO 다.** 이 필드가 없던 시절에 큐에 들어간 작업이 남아 있을 수
     * 있고, 그것들은 전부 stdin/stdout 채점이다. 채점기(Go)도 같게 읽는다.
     */
    val kind: ProblemKind = ProblemKind.JUDGE_STDIO,
    val runtimeId: String,
    val sourceCode: String,
    val timeLimitMs: Int,
    val memoryLimitMb: Int,
    val testcases: List<JudgeTestcaseMessage>,
) {
    companion object {
        /**
         * 채점기가 DB 를 읽지 않도록 채점에 필요한 값을 모두 실어 보낸다 (ADR-0004).
         *
         * 제한은 **제출한 런타임에 맞는 값**을 고른다 (#97). 런타임별 오버라이드가 없으면
         * 문제 기본값이 그대로 나온다.
         */
        fun of(submission: Submission, problem: Problem): JudgeJobMessage {
            val limits = problem.limitsFor(submission.runtimeId)
            return JudgeJobMessage(
                submissionId = submission.id,
                problemId = problem.id,
                kind = problem.problemKind,
                runtimeId = submission.runtimeId,
                sourceCode = submission.sourceCode,
                timeLimitMs = limits.timeLimitMs,
                memoryLimitMb = limits.memoryLimitMb,
                testcases = problem.testcases.map(JudgeTestcaseMessage::from),
            )
        }
    }
}
