package codekr.api.problem.admin.dto

import codekr.api.problem.entity.ProblemGitSpec
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Git 문제 등록/수정 (#654). */
data class GitSpecRequest(
    /** 시작 저장소를 만드는 명령. 없어도 된다 — 빈 저장소에서 시작하는 문제가 있다. */
    @field:Size(max = MAX_COMMANDS)
    val seedCommands: String? = null,

    @field:NotBlank(message = "정답 명령이 필요합니다.")
    @field:Size(max = MAX_COMMANDS)
    val answerCommands: String,

    @field:NotBlank(message = "끝난 뒤를 읽는 명령이 필요합니다.")
    @field:Size(max = MAX_COMMANDS)
    val verifyCommands: String,
) {
    fun toEntity(problemId: Long) = ProblemGitSpec(
        problemId = problemId,
        seedCommands = seedCommands?.ifBlank { null },
        answerCommands = answerCommands,
        verifyCommands = verifyCommands,
    )

    companion object {
        const val MAX_COMMANDS = 10_000
    }
}

/** 어드민 편집 화면에 돌려줄 Git 스펙 (#654). */
data class GitSpecResponse(
    val seedCommands: String?,
    val answerCommands: String,
    val verifyCommands: String,
) {
    companion object {
        fun from(spec: ProblemGitSpec) =
            GitSpecResponse(spec.seedCommands, spec.answerCommands, spec.verifyCommands)
    }
}
