package codekr.api.problem.admin.dto

import codekr.api.problem.entity.ProblemTemplate
import jakarta.validation.constraints.NotBlank

/** 문제가 언어/버전별로 제공할 초기 코드. */
data class TemplateRequest(
    @field:NotBlank(message = "실행 환경은 필수입니다.")
    val runtimeId: String,
    val sourceCode: String,
) {
    fun toEntity() = ProblemTemplate(runtimeId, sourceCode)
}
