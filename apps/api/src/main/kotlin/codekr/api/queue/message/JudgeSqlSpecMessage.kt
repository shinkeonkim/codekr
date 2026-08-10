package codekr.api.queue.message

import codekr.api.problem.entity.ProblemSqlSpec

/**
 * SQL 채점에 필요한 자료 (#60).
 *
 * 채점기가 DB 를 읽지 않도록 스키마와 정답 쿼리를 실어 보낸다 (ADR-0004).
 */
data class JudgeSqlSpecMessage(
    val schema: String,
    val answer: String,
    val ignoreRowOrder: Boolean,
) {
    companion object {
        fun from(spec: ProblemSqlSpec) =
            JudgeSqlSpecMessage(spec.schemaSql, spec.answerSql, spec.ignoreRowOrder)
    }
}
