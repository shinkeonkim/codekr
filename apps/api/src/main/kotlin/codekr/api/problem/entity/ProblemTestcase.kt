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
