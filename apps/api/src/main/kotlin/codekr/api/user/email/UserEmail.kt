package codekr.api.user.email

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/**
 * 확인한 추가 메일 주소 (#396).
 *
 * **로그인 주소와 다른 것이다.** 소속 인증(#240)이 학교·회사 메일을 요구하는데,
 * 로그인 주소를 그것으로 바꾸게 하면 **졸업하는 순간 로그인을 잃는다.**
 *
 * 확인된 것만 여기 들어온다 — 확인 전에는 토큰만 있고 행이 없다. 그래서 이 표의
 * 존재 자체가 "확인됨" 을 뜻한다.
 */
@Entity
@Table(name = "user_emails")
class UserEmail(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, length = 255)
    val email: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "verified_at", nullable = false)
    val verifiedAt: Instant = Instant.now()

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
}

interface UserEmailRepository : JpaRepository<UserEmail, Long> {
    fun findByUserIdOrderByIdAsc(userId: Long): List<UserEmail>
    fun existsByEmail(email: String): Boolean
    fun deleteByUserId(userId: Long)
}
