package codekr.api.problem.harness

import codekr.api.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 함수만 구현하는 문제의 하네스 (#421).
 *
 * **문제 × 언어 = 하네스 하나.** 언어마다 사용자 코드를 부르는 방법이 다르다.
 * 그리고 **하네스를 쓴 언어가 곧 허용 목록이다** (#419) — 두 곳이 같은 것을 정하면
 * 어긋나므로 목록을 따로 고르게 하지 않는다.
 */
@Entity
@Table(name = "problem_harnesses")
class ProblemHarness(

    @Column(name = "problem_id", nullable = false)
    val problemId: Long,

    @Column(name = "runtime_id", nullable = false, length = 40)
    val runtimeId: String,

    /** 사용자 코드를 부르는 **보이지 않는 코드**. 사용자에게 절대 내려가지 않는다. */
    @Column(nullable = false, columnDefinition = "text")
    var source: String,

    /** 사용자가 채울 껍데기. "빈 화면" 이 아니라 "채울 자리" 를 준다. */
    @Column(nullable = false, columnDefinition = "text")
    var template: String = "",

) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
