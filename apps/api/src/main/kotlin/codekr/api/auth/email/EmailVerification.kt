package codekr.api.auth.email

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * 발급된 인증 토큰 하나 (#233).
 *
 * **토큰 자체를 저장하지 않는다.** 표를 읽을 수 있는 사람이 남의 계정을 인증할 수
 * 있으면 안 된다 — 비밀번호를 해시로 두는 것과 같은 이유다.
 */
@Entity
@Table(name = "email_verifications")
class EmailVerification(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "token_hash", nullable = false, length = 64)
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "used_at")
    var usedAt: Instant? = null
        protected set

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    fun use(now: Instant) {
        usedAt = now
    }
}

interface EmailVerificationRepository : JpaRepository<EmailVerification, Long> {

    fun findByTokenHash(tokenHash: String): EmailVerification?

    /** 재발송 쿨다운을 재는 값. */
    fun findFirstByUserIdOrderByIdDesc(userId: Long): EmailVerification?

    /** 하루 상한. 발송량이 곧 비용이고 평판이다 (#233). */
    @Query("SELECT count(v) FROM EmailVerification v WHERE v.userId = :userId AND v.createdAt > :since")
    fun countSince(@Param("userId") userId: Long, @Param("since") since: Instant): Long
}
