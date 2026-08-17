package codekr.api.feedback

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
 * 사이트 신고·제안 (#603).
 *
 * **문제 오류 신고(#478)와 다른 것이다.** 저기는 문제를 고쳐야 끝나고 `problem_id` 가
 * 반드시 있다. 여기는 "이 버튼이 안 눌린다", "이런 기능이 있으면 좋겠다" 처럼
 * **어느 문제에도 매이지 않은 것**을 받는다.
 *
 * 전에는 이런 말을 받을 곳이 **아예 없었다** — 푸터가 GitHub 이슈 목록으로 나갔다.
 */
@Entity
@Table(name = "site_feedbacks")
class SiteFeedback(

    @Column(name = "reporter_id", nullable = false)
    val reporterId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val kind: FeedbackKind,

    @Column(nullable = false, columnDefinition = "text")
    val body: String,

    /** 어디에서 겪었는가. 재현하려면 화면이 어디였는지가 있어야 한다. */
    @Column(name = "page_url", length = 500)
    val pageUrl: String? = null,

) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: FeedbackStatus = FeedbackStatus.OPEN
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

    /** 처리한다. **한 번 처리한 것은 다시 처리하지 않는다** (#478 과 같은 규칙). */
    fun resolve(status: FeedbackStatus, resolution: String?, adminId: Long, now: Instant = Instant.now()) {
        this.status = status
        this.resolution = resolution
        this.resolvedBy = adminId
        this.resolvedAt = now
    }
}

/**
 * 무엇을 말하려는가 (#603).
 *
 * **셋뿐이다.** 더 잘게 나누면 넣는 사람이 고르느라 멈춘다 — 어드민이 본문을 읽으면
 * 어차피 알 수 있는 것들이다. 문제 내용에 대한 것은 여기가 아니라 문제 화면의
 * 오류 신고(#478)로 간다.
 */
enum class FeedbackKind(val label: String) {
    BUG("안 됩니다"),
    SUGGESTION("이렇게 해 주세요"),
    OTHER("그 밖에"),
}

enum class FeedbackStatus(val label: String) {
    OPEN("접수됨"),
    ACCEPTED("반영했음"),
    REJECTED("반영하지 않음"),
}
