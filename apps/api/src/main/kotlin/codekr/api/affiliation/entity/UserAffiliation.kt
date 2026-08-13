package codekr.api.affiliation.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 사람에게 붙은 소속 (#398).
 *
 * **여럿이다** (기획서 4절). 학부와 대학원, 학교와 회사를 동시에 가질 수 있다 —
 * 주 소속을 고르게 하면 **실제로 둘인 사람에게 하나를 부인하라고 요구하는 것**이다.
 */
@Entity
@Table(name = "user_affiliations")
class UserAffiliation(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "affiliation_id", nullable = false)
    val affiliationId: Long,

    /**
     * 어느 주소로 붙였는가.
     *
     * **그 주소를 떼면 이 소속도 함께 떨어진다** — 붙인 근거가 사라졌기 때문이다.
     * DB 의 `ON DELETE CASCADE` 가 그것을 보장한다. 코드로만 지우면 다른 경로로
     * 주소가 사라질 때 소속이 남는다.
     */
    @Column(name = "user_email_id", nullable = false)
    val userEmailId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
}
