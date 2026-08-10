package codekr.api.problem.admin.dto

import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.ExecutionLimits
import codekr.api.problem.entity.ProblemCategory
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** 문제 등록/수정에 공통으로 쓰는 요청. 테스트케이스와 템플릿은 항상 전체 치환된다. */
data class ProblemUpsertRequest(
    @field:Pattern(
        regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
        message = "slug 는 소문자, 숫자, 하이픈만 사용할 수 있습니다.",
    )
    @field:Size(min = 2, max = 120)
    val slug: String,

    @field:NotBlank @field:Size(max = 200)
    val title: String,

    val category: ProblemCategory,
    val difficulty: Difficulty,

    @field:NotBlank
    val description: String,

    val inputDescription: String? = null,
    val outputDescription: String? = null,

    @field:Min(ExecutionLimits.MIN_TIME_LIMIT_MS.toLong())
    @field:Max(ExecutionLimits.MAX_TIME_LIMIT_MS.toLong())
    val timeLimitMs: Int = ExecutionLimits.DEFAULT_TIME_LIMIT_MS,

    @field:Min(ExecutionLimits.MIN_MEMORY_LIMIT_MB.toLong())
    @field:Max(ExecutionLimits.MAX_MEMORY_LIMIT_MB.toLong())
    val memoryLimitMb: Int = ExecutionLimits.DEFAULT_MEMORY_LIMIT_MB,

    val published: Boolean = false,

    @field:Valid
    val testcases: List<TestcaseRequest> = emptyList(),

    @field:Valid
    val templates: List<TemplateRequest> = emptyList(),
)
