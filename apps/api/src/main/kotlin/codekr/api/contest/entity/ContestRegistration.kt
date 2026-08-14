package codekr.api.contest.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

/**
 * 참가 상태 (#466).
 *
 * **"등록했다" 와 "참가자다" 를 가른다.** 승인이 필요한 대회에서는 신청만 한 사람이
 * 생기고, 그 사람은 문제를 보거나 제출할 수 없다.
 *
 * 거절은 값으로 두지 않는다 — **행을 지운다.** 남기면 다시 신청할 수 없고, "왜
 * 거절됐는지" 는 관리 기록(#225)이 답한다.
 */
enum class ContestRegistrationStatus {
    PENDING,
    APPROVED,
}

/** 참가 등록 (#61). **등록하지 않으면 문제를 볼 수 없다.** */
@Entity
@Table(name = "contest_registrations")
class ContestRegistration(
    @EmbeddedId
    val id: ContestRegistrationId,
) {
    @Column(name = "registered_at", nullable = false, insertable = false, updatable = false)
    lateinit var registeredAt: Instant
        protected set

    /**
     * 승인됐는가 (#466).
     *
     * **기본이 승인이다.** 승인을 쓰지 않는 대회가 지금까지의 전부이고, 기본을
     * 대기로 두면 그 대회들이 전부 막힌다.
     */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ContestRegistrationStatus = ContestRegistrationStatus.APPROVED

    @Column(name = "decided_at")
    var decidedAt: Instant? = null

    fun approve() {
        status = ContestRegistrationStatus.APPROVED
        decidedAt = Instant.now()
    }

    val approved: Boolean get() = status == ContestRegistrationStatus.APPROVED
}

data class ContestRegistrationId(
    @Column(name = "contest_id") val contestId: Long = 0,
    @Column(name = "user_id") val userId: Long = 0,
) : Serializable
