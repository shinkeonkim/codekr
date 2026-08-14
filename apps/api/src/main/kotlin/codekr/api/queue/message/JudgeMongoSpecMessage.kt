package codekr.api.queue.message

import codekr.api.problem.entity.ProblemMongoSpec

/**
 * MongoDB 채점에 필요한 자료 (#527).
 *
 * 채점기가 DB 를 읽지 않도록 시드·정답·확인 스크립트를 실어 보낸다 (ADR-0004).
 */
data class JudgeMongoSpecMessage(
    val seed: String?,
    val answer: String,
    val verify: String,
    val ignoreOrder: Boolean,
) {
    companion object {
        fun from(spec: ProblemMongoSpec) = JudgeMongoSpecMessage(
            spec.seedScript,
            spec.answerScript,
            spec.verifyScript,
            spec.ignoreOrder,
        )
    }
}
