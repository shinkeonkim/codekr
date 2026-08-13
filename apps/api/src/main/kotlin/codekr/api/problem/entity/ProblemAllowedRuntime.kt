package codekr.api.problem.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * 이 문제를 풀 수 있는 런타임 (#419).
 *
 * **행이 하나도 없으면 전부 허용이다.** 그 문제 종류의 런타임 전부를 쓸 수 있다 —
 * 기존 문제가 그대로 돌아야 하고, 언어를 새로 들일 때 문제를 전부 손보게 하지 않으려는
 * 것이기도 하다.
 *
 * **소프트 삭제를 두지 않는다.** 허용 목록은 기록이 아니라 지금의 규칙이고, 무엇을
 * 허용했었는지는 관리 기록(#225)이 아니라 문제의 지금 상태로 충분하다.
 */
@Entity
@Table(name = "problem_allowed_runtimes")
class ProblemAllowedRuntime(

    @Column(name = "runtime_id", nullable = false, length = 40)
    var runtimeId: String,

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    var problem: Problem? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    fun assignTo(problem: Problem) {
        this.problem = problem
    }
}
