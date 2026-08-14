package codekr.api.problem.admin.dto

import codekr.api.problem.entity.ProblemMongoSpec
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** MongoDB 문제 등록/수정 (#527). */
data class MongoSpecRequest(
    /** 시작 상태를 만드는 스크립트. 없어도 된다 — 빈 상태에서 시작하는 문제가 있다. */
    @field:Size(max = MAX_SCRIPT_LENGTH)
    val seedScript: String? = null,

    @field:NotBlank(message = "정답 스크립트가 필요합니다.")
    @field:Size(max = MAX_SCRIPT_LENGTH)
    val answerScript: String,

    @field:NotBlank(message = "끝난 뒤를 읽는 스크립트가 필요합니다.")
    @field:Size(max = MAX_SCRIPT_LENGTH)
    val verifyScript: String,

    /** 기본은 순서를 지킨다 — Redis 와 같은 판단이다. */
    val ignoreOrder: Boolean = false,
) {
    fun toEntity(problemId: Long) = ProblemMongoSpec(
        problemId = problemId,
        seedScript = seedScript?.ifBlank { null },
        answerScript = answerScript,
        verifyScript = verifyScript,
        ignoreOrder = ignoreOrder,
    )

    companion object {
        /** 시드는 데이터를 포함하므로 지문보다 길어질 수 있다. */
        const val MAX_SCRIPT_LENGTH = 100_000
    }
}

data class MongoSpecResponse(
    val seedScript: String?,
    val answerScript: String,
    val verifyScript: String,
    val ignoreOrder: Boolean,
) {
    companion object {
        fun from(spec: ProblemMongoSpec) = MongoSpecResponse(
            spec.seedScript,
            spec.answerScript,
            spec.verifyScript,
            spec.ignoreOrder,
        )
    }
}
