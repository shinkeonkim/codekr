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
    /**
     * 끝난 뒤의 상태를 읽는 쿼리 (#453). 비면 결과 집합을 견준다.
     *
     * **기본값이 있는 이유:** 이 필드가 없던 시절에 큐에 들어간 작업이 남아 있을 수 있다.
     */
    val verify: String? = null,
    /** 제출에게 쓰기를 열지 (#453). 옛 작업은 읽기 전용이다. */
    val allowWrite: Boolean = false,
) {
    companion object {
        fun from(spec: ProblemSqlSpec) = JudgeSqlSpecMessage(
            spec.schemaSql,
            spec.answerSql,
            spec.ignoreRowOrder,
            spec.verifySql,
            spec.allowWrite,
        )
    }
}
