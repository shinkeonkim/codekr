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
) {
    fun toEntity(problemId: Long) = ProblemSqlSpec(problemId, schemaSql, answerSql, ignoreRowOrder)

    companion object {
        /** 스키마는 시드 데이터를 포함하므로 지문보다 길어질 수 있다. */
        const val MAX_SQL_LENGTH = 100_000
    }
}

data class SqlSpecResponse(
    val schemaSql: String,
    val answerSql: String,
    val ignoreRowOrder: Boolean,
) {
    companion object {
        fun from(spec: ProblemSqlSpec) =
            SqlSpecResponse(spec.schemaSql, spec.answerSql, spec.ignoreRowOrder)
    }
}
