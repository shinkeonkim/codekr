package codekr.api.problem.entity

import codekr.api.common.entity.SoftDeletableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * 문제가 언어/버전별로 제공하는 초기 코드.
 *
 * 런타임 레지스트리의 기본 템플릿은 "그 언어의 뼈대"일 뿐이라 문제마다 달라야 하는 함수 시그니처나
 * 입출력 형태를 담을 수 없다. 그래서 문제 단위로 런타임별 초기 코드를 따로 둔다.
 */
@Entity
@Table(name = "problem_templates")
class ProblemTemplate(

    @Column(name = "runtime_id", nullable = false, length = 40)
    var runtimeId: String,

    @Column(name = "source_code", nullable = false, columnDefinition = "text")
    var sourceCode: String,

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
