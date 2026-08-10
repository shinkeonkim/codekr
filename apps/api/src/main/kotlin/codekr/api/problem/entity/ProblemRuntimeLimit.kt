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
 * 문제의 실행 제한을 런타임(언어:버전)별로 덮어쓴 값 (#97).
 *
 * 언어마다 속도가 다르다. C++ 로 200ms 에 도는 풀이가 Python 으로는 2초가 걸린다.
 * 문제 제한 하나로 모든 언어를 재면, C++ 기준일 때는 Python 으로 아예 풀 수 없고
 * Python 기준일 때는 C++ 에서 잘못된 풀이도 통과한다.
 *
 * **행이 없으면 문제 기본값을 쓴다.** 이 표는 예외만 담는다 — 모든 문제 × 모든 런타임을
 * 채우면 런타임이 하나 늘 때마다 전부 손봐야 한다.
 */
@Entity
@Table(name = "problem_runtime_limits")
class ProblemRuntimeLimit(

    @Column(name = "runtime_id", nullable = false, length = 40)
    var runtimeId: String,

    @Column(name = "time_limit_ms", nullable = false)
    var timeLimitMs: Int,

    @Column(name = "memory_limit_mb", nullable = false)
    var memoryLimitMb: Int,

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
