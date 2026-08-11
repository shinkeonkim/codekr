package codekr.api.contest.entity

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

/** 대회 (#61). */
@Entity
@Table(name = "contests")
class Contest(
    @Column(nullable = false, length = 120)
    var slug: String,

    @Column(nullable = false, length = 200)
    var title: String,

    @Column(nullable = false)
    var description: String = "",

    @Column(name = "starts_at", nullable = false)
    var startsAt: Instant,

    @Column(name = "ends_at", nullable = false)
    var endsAt: Instant,

    /** 종료 몇 분 전부터 순위를 동결할지 (#86). 0 이면 동결하지 않는다. */
    @Column(name = "freeze_minutes", nullable = false)
    var freezeMinutes: Int = DEFAULT_FREEZE_MINUTES,

    /**
     * 같은 문제를 다시 낼 수 있기까지의 간격 (#189).
     *
     * 대회마다 완화할 수 있지만 하한이 있다 — `SubmissionCooldown.MINIMUM`.
     */
    @Column(name = "submission_cooldown_seconds")
    var submissionCooldownSeconds: Int = DEFAULT_COOLDOWN_SECONDS,

    @Column(name = "registration_open_during", nullable = false)
    var registrationOpenDuring: Boolean = true,

    @Column(name = "created_by")
    val createdBy: Long? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ContestStatus = ContestStatus.DRAFT
        protected set

    /**
     * 최종 순위를 공개한 시각 (#86).
     *
     * **자동이 아니라 어드민의 행위다.** 종료와 동시에 자동 공개하면, 종료 직후 발견된
     * 문제(테스트케이스 오류 등)를 바로잡을 틈이 없다.
     */
    @Column(name = "unfrozen_at")
    var unfrozenAt: Instant? = null
        protected set

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
        protected set

    /** 지금 이 대회가 어느 단계인가. **시각이 정한다.** */
    fun phaseAt(now: Instant): ContestPhase = when (status) {
        ContestStatus.DRAFT -> ContestPhase.DRAFT
        ContestStatus.CANCELED -> ContestPhase.CANCELED
        ContestStatus.ARCHIVED -> ContestPhase.ARCHIVED
        ContestStatus.PUBLISHED -> when {
            now < startsAt -> ContestPhase.SCHEDULED
            now < endsAt -> ContestPhase.RUNNING
            else -> ContestPhase.ENDED
        }
    }

    /**
     * 순위가 동결되는 시각. 동결하지 않으면 null.
     */
    val freezeAt: Instant?
        get() = if (freezeMinutes <= 0) null else endsAt.minusSeconds(freezeMinutes * 60L)

    /**
     * 지금 참가자에게 순위가 감춰지는가 (#86).
     *
     * 해제하면 감추지 않는다. 해제는 종료 후 어드민이 한다.
     */
    fun frozenAt(now: Instant): Boolean {
        if (unfrozenAt != null) return false
        val freeze = freezeAt ?: return false
        return now >= freeze && phaseAt(now) != ContestPhase.DRAFT
    }

    fun canRegisterAt(now: Instant): Boolean = when (phaseAt(now)) {
        ContestPhase.SCHEDULED -> true
        ContestPhase.RUNNING -> registrationOpenDuring
        else -> false
    }

    fun publish() {
        status = ContestStatus.PUBLISHED
    }

    fun cancel() {
        status = ContestStatus.CANCELED
    }

    fun archive() {
        status = ContestStatus.ARCHIVED
    }

    /** 최종 순위를 공개한다. 이미 공개했으면 아무 일도 없다 — 시각을 덮어쓰지 않는다. */
    fun unfreeze(now: Instant) {
        if (unfrozenAt == null) unfrozenAt = now
    }

    fun delete() {
        deletedAt = Instant.now()
    }

    companion object {
        /** 종료 30분 전. 짧은 대회(2시간)가 많을 것이라 1시간은 과하다 (#86). */
        const val DEFAULT_FREEZE_MINUTES = 30

        /** 대회 기본 제출 간격(초). 일반 제출(30초)보다 짧다 — 대회는 고쳐 내는 속도가 중요하다. */
        const val DEFAULT_COOLDOWN_SECONDS = 20
    }
}
