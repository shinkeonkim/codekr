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
 * 함수형 문제의 언어별 하네스 (#446, #421).
 *
 * **절대 사용자에게 보이면 안 되는 코드다.** 정답의 일부나 판정 방식이 들어간다 —
 * 그래서 문제 상세 응답에도, 오류 메시지에도(#445 가 실행기에서 지운다) 나가지 않는다.
 *
 * `ProblemTemplate`(#12) 과 표를 나눈 이유가 그것이다. 그쪽은 **보여 주려고** 있는
 * 것이고 이쪽은 **감추려고** 있다.
 */
@Entity
@Table(name = "problem_harnesses")
class ProblemHarness(

    @Column(name = "runtime_id", nullable = false, length = 40)
    var runtimeId: String,

    @Column(nullable = false, columnDefinition = "text")
    var source: String,

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
