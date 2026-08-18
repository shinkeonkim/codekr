package codekr.api.problem.repository

import codekr.api.problem.entity.ProblemKind
import codekr.api.runtime.RuntimeDefinition

/**
 * 목록을 언어·런타임으로 거를 때 실제로 무엇을 찾을지 (#618).
 *
 * ## 두 단으로 고른다
 *
 * **언어로 크게 고르고, 원하면 런타임까지 좁힌다.** "파이썬으로 풀 수 있는 문제" 를
 * 찾는 사람에게 `python:3.11`·`3.12`·`3.13` 을 따로 고르게 하면 세 번 걸러야 한다.
 * 그래도 버전 칸이 필요한 자리가 있다 — 문제가 특정 버전만 허용할 수 있고(#419),
 * `sql:postgres16` 과 `sql:mariadb11` 은 **같은 "SQL" 이지만 문법이 다르다**(#454).
 *
 * ## 어려운 것은 "제한 없음" 이다
 *
 * `problem_allowed_runtimes` 에 **행이 하나도 없으면 전부 허용**이다 (#419). 목록의
 * 대부분이 그렇다. 그래서 허용 표만 조인하면 **언어를 지정한 소수만 걸리고**, 틀린
 * 목록이 그럴듯하게 나온다.
 *
 * 고른 언어로 풀 수 있는 문제는 **둘의 합**이다.
 *
 * - 그 런타임을 **명시적으로 허용한** 문제
 * - **아무것도 지정하지 않았고**, 그 유형을 그 런타임이 풀 수 있는 문제
 *
 * 뒤엣것을 위해 [kinds] 가 있다 — `RuntimeDefinition.canSolve` 가 정하는 그 규칙이다.
 */
data class RuntimeFilter(
    /** 찾을 런타임 id 들. 이 중 **하나라도** 허용하면 걸린다. */
    val runtimeIds: List<String>,
    /**
     * 허용 목록이 비어 있는 문제를 걸 유형들.
     *
     * 비면 "제한 없음" 문제는 어느 것도 걸리지 않는다 — 고른 런타임이 아무 유형도
     * 풀지 못한다는 뜻이라 그것이 맞다.
     */
    val kinds: List<ProblemKind>,
) {
    val isEmpty: Boolean get() = runtimeIds.isEmpty()

    companion object {
        /** 런타임 id 앞부분이 언어다 — `python:3.13` → `python`. */
        fun languageOf(runtimeId: String): String = runtimeId.substringBefore(':')

        /**
         * 고른 언어·런타임을 실제 조건으로 바꾼다.
         *
         * [runtimeId] 가 있으면 그것 하나로 좁힌다. 없고 [language] 만 있으면 그 언어의
         * 런타임 전부다. **모르는 값이면 빈 결과가 아니라 [runtimeIds] 가 비어**,
         * 부르는 쪽이 "거르지 않음" 과 구분할 수 있다.
         */
        fun of(
            language: String?,
            runtimeId: String?,
            runtimes: List<RuntimeDefinition>,
        ): RuntimeFilter? {
            val selected = when {
                !runtimeId.isNullOrBlank() -> runtimes.filter { it.id == runtimeId }
                !language.isNullOrBlank() -> runtimes.filter { languageOf(it.id) == language }
                else -> return null
            }
            if (selected.isEmpty()) return RuntimeFilter(emptyList(), emptyList())

            return RuntimeFilter(
                runtimeIds = selected.map { it.id },
                // 하나라도 풀 수 있으면 그 유형은 후보다.
                kinds = ProblemKind.entries.filter { kind -> selected.any { it.canSolve(kind) } },
            )
        }
    }
}
