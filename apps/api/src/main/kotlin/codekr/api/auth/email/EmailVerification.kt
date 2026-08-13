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

    /**
     * 어느 주소를 확인하려는 토큰인가 (#396).
     *
     * **비우면 로그인 주소다** — 가입 때 보내는 그것이다. 값이 있으면 추가 주소를
     * 확인하는 토큰이고, 확인되면 user_emails 에 행이 생긴다.
     *
     * 토큰 장치를 두 벌 만들지 않는 이유: 해시 저장·만료·쿨다운·하루 상한이 이미
     * 여기 있다. 갈라 두면 한쪽만 고쳐지는 날이 온다.
     */
    @Column(length = 255)
    val email: String? = null,
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

    /**
     * 재발송 쿨다운을 재는 값.
     *
     * **주소별로 잰다** (#396). 쿨다운이 막으려는 것은 "같은 주소로 계속 보내기" 인데,
     * 사람 단위로 재면 **다른 주소를 더하는 것까지 막힌다** — 학교 메일과 회사 메일을
     * 이어서 넣을 수 없게 된다. 하루 상한은 그대로 사람 단위다(발송량이 곧 비용이다).
     */
    fun findFirstByUserIdAndEmailOrderByIdDesc(userId: Long, email: String?): EmailVerification?

    /** 하루 상한. 발송량이 곧 비용이고 평판이다 (#233). */
    @Query("SELECT count(v) FROM EmailVerification v WHERE v.userId = :userId AND v.createdAt > :since")
    fun countSince(@Param("userId") userId: Long, @Param("since") since: Instant): Long
}
