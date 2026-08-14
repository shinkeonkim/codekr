package codekr.api.problem.admin.dto

import codekr.api.problem.entity.ProblemRedisSpec
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Redis 문제 등록/수정 (#455). */
data class RedisSpecRequest(
    /** 시작 상태를 만드는 명령. 없어도 된다 — 빈 상태에서 시작하는 문제가 있다. */
    @field:Size(max = MAX_COMMANDS_LENGTH)
    val seedCommands: String? = null,

    @field:NotBlank(message = "정답 명령이 필요합니다.")
    @field:Size(max = MAX_COMMANDS_LENGTH)
    val answerCommands: String,

    @field:NotBlank(message = "끝난 뒤의 상태를 읽는 명령이 필요합니다.")
    @field:Size(max = MAX_COMMANDS_LENGTH)
    val verifyCommands: String,

    /** 기본은 순서를 지킨다 — 정렬 집합·리스트에서 순서는 자료의 일부다. */
    val ignoreOrder: Boolean = false,
) {
    fun toEntity(problemId: Long) = ProblemRedisSpec(
        problemId = problemId,
        seedCommands = seedCommands?.ifBlank { null },
        answerCommands = answerCommands,
        verifyCommands = verifyCommands,
        ignoreOrder = ignoreOrder,
    )

    companion object {
        /** 시드는 데이터를 포함하므로 지문보다 길어질 수 있다. */
        const val MAX_COMMANDS_LENGTH = 100_000
    }
}

data class RedisSpecResponse(
    val seedCommands: String?,
    val answerCommands: String,
    val verifyCommands: String,
    val ignoreOrder: Boolean,
) {
    companion object {
        fun from(spec: ProblemRedisSpec) = RedisSpecResponse(
            spec.seedCommands,
            spec.answerCommands,
            spec.verifyCommands,
            spec.ignoreOrder,
        )
    }
}
