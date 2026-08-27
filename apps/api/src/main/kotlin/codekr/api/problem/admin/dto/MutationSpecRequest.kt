package codekr.api.problem.admin.dto

import codekr.api.problem.entity.ProblemMutant
import codekr.api.problem.entity.ProblemMutationSpec
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 테스트 작성 문제 등록/수정 (#652). */
data class MutationSpecRequest(
    /** 올바른 구현. 사용자의 시험이 이것은 통과시켜야 한다. */
    @field:NotBlank(message = "올바른 구현이 필요합니다.")
    @field:Size(max = MAX_SOURCE)
    val referenceSource: String,

    /**
     * 버그를 심은 구현들.
     *
     * **하나도 없으면 문제가 아니다** — 아무것도 확인하지 않는 시험이 통과한다.
     * 검증이 막는다.
     */
    @field:Valid
    @field:Size(max = MAX_MUTANTS, message = "버그 심은 구현은 ${MAX_MUTANTS}개까지 넣을 수 있습니다.")
    val mutants: List<MutantRequest> = emptyList(),
) {
    fun toSpec(problemId: Long) = ProblemMutationSpec(problemId, referenceSource)

    /** 번호는 **서버가 매긴다** — 하네스가 그 순서로 돌리고 기대값의 줄 순서와 맞는다. */
    fun toMutants(problemId: Long) = mutants.mapIndexed { index, mutant ->
        ProblemMutant(problemId, index + 1, mutant.label?.ifBlank { null }, mutant.source)
    }

    companion object {
        const val MAX_SOURCE = 20_000

        /**
         * **실행 시간이 구현 수에 비례한다.** 시험 하나가 (1 + 뮤턴트 수)번 돌므로,
         * 상한이 없으면 한 제출이 실행기를 오래 붙든다. 열이면 열한 번이다.
         */
        const val MAX_MUTANTS = 10
    }
}

/** 버그를 심은 구현 하나 (#652). */
data class MutantRequest(
    /** 무엇을 심었는지. **사용자에게 나가지 않는다** — 나가면 답을 주는 것이다. */
    @field:Size(max = 200)
    val label: String? = null,

    @field:NotBlank(message = "구현 내용이 필요합니다.")
    @field:Size(max = MutationSpecRequest.MAX_SOURCE)
    val source: String,
)

/** 어드민 편집 화면에 돌려줄 테스트 작성 스펙 (#652). */
data class MutationSpecResponse(
    val referenceSource: String,
    val mutants: List<MutantResponse>,
) {
    data class MutantResponse(val label: String?, val source: String)

    companion object {
        fun of(spec: ProblemMutationSpec, mutants: List<ProblemMutant>) = MutationSpecResponse(
            referenceSource = spec.referenceSource,
            mutants = mutants.sortedBy { it.seq }.map { MutantResponse(it.label, it.source) },
        )
    }
}
