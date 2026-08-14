package codekr.api.problem.dto

import codekr.api.problem.entity.ProblemFile

/**
 * 여러 파일을 완성하는 문제의 파일 하나 (#457).
 *
 * **어드민과 푸는 화면이 같은 모양을 본다.** 시작 코드와 "고칠 수 있는가" 는 둘 다에게
 * 필요하고, 갈라 두면 한쪽만 고쳐졌을 때 화면이 서로 다른 것을 그린다.
 */
data class ProblemFileResponse(
    val runtimeId: String,
    val name: String,
    val template: String,
    val editable: Boolean,
) {
    companion object {
        fun from(file: ProblemFile) =
            ProblemFileResponse(file.runtimeId, file.name, file.template, file.editable)
    }
}
