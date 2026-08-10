package codekr.api.contest.board

import codekr.api.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 대회 질의 (#147).
 *
 * 참가자가 묻고 운영자가 답한다. **답변은 공개/비공개가 갈린다** —
 * 한 사람에게만 준 정보가 유리하게 작용하면 안 되는 질문이 있고,
 * 반대로 그 사람만의 사정인 질문도 있다.
 */
@Entity
@Table(name = "contest_questions")
class ContestQuestion(
    @Column(name = "contest_id", nullable = false)
    val contestId: Long,

    /** 어느 문제에 대한 질문인지. 없으면 대회 전체에 대한 질문이다. */
    @Column(name = "problem_id")
    val problemId: Long? = null,

    @Column(name = "asker_id", nullable = false)
    val askerId: Long,

    @Column(nullable = false)
    val body: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column
    var answer: String? = null
        protected set

    @Column(name = "answer_public", nullable = false)
    var answerPublic: Boolean = false
        protected set

    @Column(name = "answered_by")
    var answeredBy: Long? = null
        protected set

    @Column(name = "answered_at")
    var answeredAt: Instant? = null
        protected set

    val isAnswered: Boolean get() = answer != null

    fun answer(text: String, public: Boolean, by: Long) {
        answer = text
        answerPublic = public
        answeredBy = by
        answeredAt = Instant.now()
    }

    /** 이 사람이 이 질의를 볼 수 있는가. */
    fun isVisibleTo(viewerId: Long?, isManager: Boolean): Boolean =
        isManager || askerId == viewerId || (isAnswered && answerPublic)
}
