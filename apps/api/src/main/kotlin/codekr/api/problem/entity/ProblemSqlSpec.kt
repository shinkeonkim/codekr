package codekr.api.problem.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * SQL 문제의 스펙 (#60).
 *
 * **정답을 결과 집합이 아니라 쿼리로 저장한다.** 시드 데이터가 바뀌면 기대 결과도
 * 따라간다 — 결과를 박아 두면 시드를 고칠 때마다 정답도 손으로 고쳐야 하고,
 * 고치는 것을 잊으면 모든 제출이 틀리게 된다.
 */
@Entity
@Table(name = "problem_sql_specs")
class ProblemSqlSpec(
    @Id
    @Column(name = "problem_id")
    val problemId: Long,

    /** 스키마와 시드. 슈퍼유저로 주입한다. */
    @Column(name = "schema_sql", nullable = false)
    var schemaSql: String,

    @Column(name = "answer_sql", nullable = false)
    var answerSql: String,

    /**
     * 행 순서를 무시할지. **기본은 무시다.**
     *
     * 문제가 정렬을 요구하지 않는데 순서를 비교하면 맞는 답이 틀린 것으로 나온다.
     * 정렬이 문제의 일부인 경우에만 끈다.
     */
    @Column(name = "ignore_row_order", nullable = false)
    var ignoreRowOrder: Boolean = true,
)
