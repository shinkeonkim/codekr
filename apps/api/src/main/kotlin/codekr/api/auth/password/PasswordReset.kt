package codekr.api.auth.password

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
 * 발급된 재설정 토큰 하나 (#315).
 *
 * 인증 토큰(#233)과 모양은 같지만 **무게가 다르다** — 이쪽은 로그인 수단 자체를 갈아
 * 끼운다. 그래서 만료가 훨씬 짧다.
 */
@Entity
@Table(name = "password_resets")
class PasswordReset(
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

interface PasswordResetRepository : JpaRepository<PasswordReset, Long> {

    fun findByTokenHash(tokenHash: String): PasswordReset?

    fun findFirstByUserIdOrderByIdDesc(userId: Long): PasswordReset?

    @Query("SELECT count(r) FROM PasswordReset r WHERE r.userId = :userId AND r.createdAt > :since")
    fun countSince(@Param("userId") userId: Long, @Param("since") since: Instant): Long
}
