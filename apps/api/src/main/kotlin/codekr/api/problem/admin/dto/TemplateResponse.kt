package codekr.api.problem.admin.dto

import codekr.api.problem.entity.ProblemTemplate

data class TemplateResponse(val runtimeId: String, val sourceCode: String) {
    companion object {
        fun from(template: ProblemTemplate) = TemplateResponse(template.runtimeId, template.sourceCode)
    }
}
