package codekr.api.contest.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

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
}

data class ContestRegistrationId(
    @Column(name = "contest_id") val contestId: Long = 0,
    @Column(name = "user_id") val userId: Long = 0,
) : Serializable
