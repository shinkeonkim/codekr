package codekr.api.problem.dto

import codekr.api.runtime.RuntimeDefinition

/**
 * 문제 상세에서 고를 수 있는 실행 환경.
 * [template] 은 문제가 지정한 초기 코드이며, 없으면 런타임 기본 템플릿이 들어간다.
 */
data class ProblemRuntimeResponse(
    val id: String,
    val label: String,
    val monacoLanguage: String,
    val template: String,
) {
    companion object {
        fun of(runtime: RuntimeDefinition, problemTemplate: String?) = ProblemRuntimeResponse(
            id = runtime.id,
            label = runtime.label,
            monacoLanguage = runtime.monacoLanguage,
            template = problemTemplate ?: runtime.template,
        )
    }
}
