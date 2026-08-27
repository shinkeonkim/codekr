package codekr.api.queue.message

import codekr.api.problem.entity.ProblemMutant
import codekr.api.problem.entity.ProblemMutationSpec

/**
 * 테스트 작성 채점에 필요한 자료 (#652).
 *
 * 채점기가 DB 를 읽지 않도록 구현들을 실어 보낸다 (ADR-0004).
 * **`label` 은 싣지 않는다** — 채점에 쓰이지 않고, 실으면 새어 나갈 자리가 하나 는다.
 */
data class JudgeMutationSpecMessage(
    val reference: String,
    val mutants: List<String>,
) {
    companion object {
        fun of(spec: ProblemMutationSpec, mutants: List<ProblemMutant>) =
            JudgeMutationSpecMessage(spec.referenceSource, mutants.sortedBy { it.seq }.map { it.source })
    }
}
