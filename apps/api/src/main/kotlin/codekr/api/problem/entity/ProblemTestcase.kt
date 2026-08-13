package codekr.api.problem.entity

import codekr.api.common.entity.SoftDeletableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "problem_testcases")
class ProblemTestcase(

    @Column(nullable = false)
    var seq: Int,

    @Column(nullable = false, columnDefinition = "text")
    var input: String,

    @Column(name = "expected_output", nullable = false, columnDefinition = "text")
    var expectedOutput: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var visibility: TestcaseVisibility = TestcaseVisibility.HIDDEN,

    /**
     * 부분 점수 묶음 (#473). null 이면 묶음이 없는 문제다.
     *
     * **묶음 안을 다 맞혀야 그 점수를 받는다** (IOI 관례) — 케이스마다 점수를 주면
     * 묶음의 뜻이 없어진다.
     */
    @Column(name = "group_no")
    var groupNo: Int? = null,

) : SoftDeletableEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    var problem: Problem? = null

    internal fun assignTo(problem: Problem) {
        this.problem = problem
    }
}
