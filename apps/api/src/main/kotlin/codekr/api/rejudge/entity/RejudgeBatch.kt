package codekr.api.rejudge.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 재채점 한 번의 기록 (#107).
 *
 * 이유(reason)를 필수로 둔 이유: 결과가 바뀐 사용자에게 **왜 바뀌었는지** 알려야 한다.
 * "판정이 바뀌었습니다" 만 보내면 사용자는 우리가 임의로 바꿨다고 받아들인다.
 */
@Entity
@Table(name = "rejudge_batches")
class RejudgeBatch(

    @Column(name = "problem_id", nullable = false)
    val problemId: Long,

    @Column(nullable = false, length = 200)
    val reason: String,

    @Column(name = "requested_by", nullable = false)
    val requestedBy: Long,

    @Column(name = "target_count", nullable = false)
    val targetCount: Int,

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "changed_count", nullable = false)
    var changedCount: Int = 0

    @Column(name = "finished_at")
    var finishedAt: Instant? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    val isFinished: Boolean get() = finishedAt != null

    fun recordChange() {
        changedCount++
    }

    fun finish(now: Instant = Instant.now()) {
        if (finishedAt == null) finishedAt = now
    }
}
