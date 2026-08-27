package codekr.api.queue.message

import codekr.api.problem.entity.ProblemRegexSpec

/**
 * 정규식 채점에 필요한 자료 (#653).
 *
 * 채점기가 DB 를 읽지 않도록 확인 문자열과 판정 방식을 실어 보낸다 (ADR-0004).
 *
 * **정답 패턴이 없다.** 확인 문자열의 `+`/`-` 가 곧 기대값이기 때문이다 —
 * 정답 패턴으로 기대값을 만들면 출제자가 실수한 패턴이 그대로 정답이 된다.
 */
data class JudgeRegexSpecMessage(
    val cases: String,
    val fullMatch: Boolean,
    val ignoreCase: Boolean,
) {
    companion object {
        fun from(spec: ProblemRegexSpec) =
            JudgeRegexSpecMessage(spec.cases, spec.fullMatch, spec.ignoreCase)
    }
}
