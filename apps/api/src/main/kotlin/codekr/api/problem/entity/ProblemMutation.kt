package codekr.api.problem.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 테스트 작성 문제의 설정 (#652).
 *
 * **정답 시험을 두지 않는다.** 기대값은 구조가 정한다 — 올바른 구현은 통과, 버그 심은
 * 구현은 전부 실패다. 정답 시험으로 기대값을 만들면 **출제자가 놓친 버그는 아무도 잡지
 * 못한다**: 그 시험이 못 잡은 뮤턴트가 "잡지 않아도 되는 것" 이 되어 버린다.
 */
@Entity
@Table(name = "problem_mutation_specs")
class ProblemMutationSpec(
    @Id
    @Column(name = "problem_id")
    val problemId: Long,

    /** 올바른 구현. 사용자의 시험이 이것은 **통과시켜야** 한다. */
    @Column(name = "reference_source", nullable = false)
    var referenceSource: String,
)

/**
 * 버그를 심은 구현 하나 (#652).
 *
 * **[label] 은 사용자에게 나가지 않는다.** 무엇을 심었는지가 곧 무엇을 확인해야 하는지라,
 * 나가면 답을 주는 것이 된다. 출제자가 자기 문제를 읽을 때 쓴다.
 */
@Entity
@Table(name = "problem_mutants")
class ProblemMutant(
    @Column(name = "problem_id", nullable = false)
    val problemId: Long,

    @Column(nullable = false)
    val seq: Int,

    @Column(length = 200)
    val label: String? = null,

    @Column(nullable = false)
    val source: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
