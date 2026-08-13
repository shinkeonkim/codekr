package codekr.api.problem.admin.dto

import codekr.api.problem.entity.ProblemSqlSpec
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** SQL 문제 등록/수정 (#60). */
data class SqlSpecRequest(
    @field:NotBlank(message = "스키마와 시드 데이터가 필요합니다.")
    @field:Size(max = MAX_SQL_LENGTH)
    val schemaSql: String,

    @field:NotBlank(message = "정답 쿼리가 필요합니다.")
    @field:Size(max = MAX_SQL_LENGTH)
    val answerSql: String,

    val ignoreRowOrder: Boolean = true,

    /** 끝난 뒤의 상태를 읽는 쿼리 (#453). 비어 있으면 결과 집합을 견준다. */
    @field:Size(max = MAX_SQL_LENGTH)
    val verifySql: String? = null,

    /** 제출에게 쓰기를 열지 (#453). **권한으로 연다.** */
    val allowWrite: Boolean = false,
) {
    fun toEntity(problemId: Long) = ProblemSqlSpec(
        problemId,
        schemaSql,
        answerSql,
        ignoreRowOrder,
        verifySql?.ifBlank { null },
        allowWrite,
    )

    companion object {
        /** 스키마는 시드 데이터를 포함하므로 지문보다 길어질 수 있다. */
        const val MAX_SQL_LENGTH = 100_000
    }
}

data class SqlSpecResponse(
    val schemaSql: String,
    val answerSql: String,
    val ignoreRowOrder: Boolean,
    val verifySql: String?,
    val allowWrite: Boolean,
) {
    companion object {
        fun from(spec: ProblemSqlSpec) = SqlSpecResponse(
            spec.schemaSql,
            spec.answerSql,
            spec.ignoreRowOrder,
            spec.verifySql,
            spec.allowWrite,
        )
    }
}
