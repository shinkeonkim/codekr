package codekr.api.problem.admin.dto

import codekr.api.problem.entity.ProblemRegexSpec
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 정규식 문제 등록/수정 (#653). */
data class RegexSpecRequest(
    /**
     * 확인할 문자열. 한 줄에 하나, 첫 글자가 `+`(맞아야 함) 또는 `-`(맞으면 안 됨).
     *
     * **맞으면 안 되는 것이 없으면 문제가 아니다** — `.*` 가 통과한다. 검증이 막는다.
     */
    @field:NotBlank(message = "확인할 문자열이 필요합니다.")
    @field:Size(max = MAX_CASES)
    val cases: String,

    /** 기본은 전체 일치다 — 부분 일치는 `.` 하나로도 통과하는 문제가 많다. */
    val fullMatch: Boolean = true,
    val ignoreCase: Boolean = false,
) {
    fun toEntity(problemId: Long) = ProblemRegexSpec(
        problemId = problemId,
        cases = cases.trim(),
        fullMatch = fullMatch,
        ignoreCase = ignoreCase,
    )

    companion object {
        const val MAX_CASES = 20_000
    }
}

/** 어드민 편집 화면에 돌려줄 정규식 스펙 (#653). */
data class RegexSpecResponse(
    val cases: String,
    val fullMatch: Boolean,
    val ignoreCase: Boolean,
) {
    companion object {
        fun from(spec: ProblemRegexSpec) =
            RegexSpecResponse(spec.cases, spec.fullMatch, spec.ignoreCase)
    }
}
