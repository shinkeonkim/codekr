package codekr.api.queue.message

import codekr.api.problem.entity.ProblemGitSpec

/**
 * Git 채점에 필요한 자료 (#654).
 *
 * 채점기가 DB 를 읽지 않도록 시드·정답·확인 명령을 실어 보낸다 (ADR-0004).
 */
data class JudgeGitSpecMessage(
    val seed: String?,
    val answer: String,
    val verify: String,
) {
    companion object {
        fun from(spec: ProblemGitSpec) =
            JudgeGitSpecMessage(spec.seedCommands, spec.answerCommands, spec.verifyCommands)
    }
}
