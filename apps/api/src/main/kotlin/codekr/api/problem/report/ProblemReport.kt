package codekr.api.problem.report

import codekr.api.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 문제 오류 신고 (#478).
 *
 * **질문(#139)과 다른 것이다** — 질문은 답이 달리면 끝나고 오류 신고는 **문제를 고쳐야**
 * 끝난다. 그리고 안 보고 넘기면 모든 제출이 계속 잘못 채점된다.
 */
@Entity
@Table(name = "problem_reports")
class ProblemReport(

    @Column(name = "problem_id", nullable = false)
    val problemId: Long,

    @Column(name = "reporter_id", nullable = false)
    val reporterId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val kind: ReportKind,

    @Column(nullable = false, columnDefinition = "text")
    val body: String,

) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReportStatus = ReportStatus.OPEN
        protected set

    @Column(columnDefinition = "text")
    var resolution: String? = null
        protected set

    @Column(name = "resolved_by")
    var resolvedBy: Long? = null
        protected set

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null
        protected set

    /**
     * 처리한다. **한 번 처리한 것은 다시 처리하지 않는다** — 두 번 처리되면 신고한
     * 사람에게 알림이 두 번 가고, 어느 것이 결론인지 알 수 없다.
     */
    fun resolve(status: ReportStatus, resolution: String?, adminId: Long, now: Instant = Instant.now()) {
        this.status = status
        this.resolution = resolution
        this.resolvedBy = adminId
        this.resolvedAt = now
    }
}

/**
 * 무엇이 잘못됐는가 (#478).
 *
 * **가장 흔한 둘이 앞에 있다** — 테스트케이스가 빠져 틀린 코드가 통과하는 것과,
 * 제약이 안 적혀 지문만으로는 풀 수 없는 것.
 */
enum class ReportKind(val label: String) {
    MISSING_TESTCASE("테스트케이스 부족"),
    MISSING_CONSTRAINT("제약 조건 누락"),
    WRONG_STATEMENT("지문 오류"),
    WRONG_ANSWER("정답이 틀림"),
    OTHER("그 밖에"),
}

enum class ReportStatus(val label: String) {
    OPEN("접수됨"),
    ACCEPTED("고쳤음"),
    REJECTED("문제 없음"),
}
