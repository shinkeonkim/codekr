package codekr.api.problem.dto

import codekr.api.problem.entity.ResolvedLimits
import codekr.api.problem.entity.ProblemFile
import codekr.api.runtime.RuntimeDefinition

/**
 * 문제 상세에서 고를 수 있는 실행 환경.
 *
 * [template] 은 문제가 지정한 초기 코드이며, 없으면 런타임 기본 템플릿이 들어간다.
 * 제한은 **이 런타임으로 실행할 때 실제로 적용되는 값**이다 (#97) — 문제 기본값과
 * 다를 수 있으므로 화면이 선택한 언어에 맞는 숫자를 보여줄 수 있어야 한다.
 */
data class ProblemRuntimeResponse(
    val id: String,
    val label: String,
    val monacoLanguage: String,
    val template: String,
    val timeLimitMs: Int,
    val memoryLimitMb: Int,
    /** 문제 기본값이 아니라 이 런타임 전용 값이 쓰였는지. 화면이 그 사실을 알려줄 수 있게. */
    val limitOverridden: Boolean,
    /**
     * 이 런타임으로 풀 때 채워야 하는 파일들 (#457). 비면 파일 하나짜리다.
     *
     * **런타임 안에 둔다.** 파일 이름은 언어를 따라 갈리므로(`Main.java` vs `main.py`)
     * 문제 단위로 내려주면 화면이 언어를 바꿀 때마다 무엇이 맞는 목록인지 알 수 없다.
     */
    val files: List<ProblemFileResponse> = emptyList(),
) {
    companion object {
        fun of(
            runtime: RuntimeDefinition,
            problemTemplate: String?,
            limits: ResolvedLimits,
            files: List<ProblemFile> = emptyList(),
        ) =
            ProblemRuntimeResponse(
                id = runtime.id,
                label = runtime.label,
                monacoLanguage = runtime.monacoLanguage,
                files = files.map(ProblemFileResponse::from),
                template = problemTemplate ?: runtime.template,
                timeLimitMs = limits.timeLimitMs,
                memoryLimitMb = limits.memoryLimitMb,
                limitOverridden = limits.overridden,
            )
    }
}
