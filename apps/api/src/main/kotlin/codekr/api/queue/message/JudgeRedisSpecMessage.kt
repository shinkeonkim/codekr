package codekr.api.queue.message

import codekr.api.problem.entity.ProblemRedisSpec

/**
 * Redis 채점에 필요한 자료 (#455).
 *
 * 채점기가 DB 를 읽지 않도록 시드·정답·확인 명령을 실어 보낸다 (ADR-0004).
 */
data class JudgeRedisSpecMessage(
    val seed: String?,
    val answer: String,
    val verify: String,
    val ignoreOrder: Boolean,
) {
    companion object {
        fun from(spec: ProblemRedisSpec) = JudgeRedisSpecMessage(
            spec.seedCommands,
            spec.answerCommands,
            spec.verifyCommands,
            spec.ignoreOrder,
        )
    }
}
