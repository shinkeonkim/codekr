package codekr.api.contest.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

/**
 * 대회에 배정된 문제와 배점 (#61).
 *
 * 배점은 난이도에서 자동으로 뽑지 않고 어드민이 정한다 — 대회 설계의 자유도다
 * (기획서 §5).
 */
@Entity
@Table(name = "contest_problems")
class ContestProblem(
    @EmbeddedId
    val id: ContestProblemId,

    /** 대회 안에서의 순번. 화면에는 A, B, C… 로 보인다. */
    @Column(nullable = false)
    var seq: Int,

    @Column(nullable = false)
    var score: Int,
) {
    /**
     * 문제가 잘못된 것으로 드러나 제외된 시각.
     *
     * 지우지 않는 이유는 그 문제로 낸 제출이 남아 있기 때문이다. 제외는 프리즈로
     * 감추지 않고 **즉시 반영한다** (#86) — 없어진 문제를 계속 푸는 것은 시간 낭비다.
     */
    @Column(name = "excluded_at")
    var excludedAt: Instant? = null
        protected set

    val problemId: Long get() = id.problemId

    val isExcluded: Boolean get() = excludedAt != null

    fun exclude() {
        if (excludedAt == null) excludedAt = Instant.now()
    }

    fun restore() {
        excludedAt = null
    }
}

data class ContestProblemId(
    @Column(name = "contest_id") val contestId: Long = 0,
    @Column(name = "problem_id") val problemId: Long = 0,
) : Serializable
