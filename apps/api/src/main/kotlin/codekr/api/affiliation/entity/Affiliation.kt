package codekr.api.affiliation.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** 학교인가 회사인가 (#397). 화면이 나눠 보여 준다. */
enum class AffiliationKind(val label: String) {
    SCHOOL("학교"),
    COMPANY("회사"),
}

/**
 * 소속 (#397, #240 2단계).
 *
 * **어드민이 등록한다.** 자동으로 만들면 `@gmail.com` 이 "지메일 대학" 이 되고 오타
 * 도메인이 소속으로 쌓인다 — 소속은 몇 개 안 되고 자주 늘지 않으므로 손으로 넣는
 * 비용이 작다.
 */
@Entity
@Table(name = "affiliations")
class Affiliation(
    @Column(nullable = false, length = 100)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var kind: AffiliationKind,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    /**
     * 소프트 삭제 (ADR-0007).
     *
     * **지우지 않는 이유**: 이미 이 소속이 붙은 사람들이 있다. 행을 지우면 그들의
     * 소속이 무엇이었는지 아무도 모르게 되고, 랭킹의 옛 순위도 설명할 수 없다.
     */
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
        protected set

    fun delete(now: Instant = Instant.now()) {
        deletedAt = now
    }
}

/**
 * 그 소속의 메일 도메인 (#397).
 *
 * 여럿일 수 있다 — `postech.ac.kr` 과 `postech.edu` 처럼.
 */
@Entity
@Table(name = "affiliation_domains")
class AffiliationDomain(
    @Column(name = "affiliation_id", nullable = false)
    val affiliationId: Long,

    /** **소문자로만 저장한다.** `SNU.ac.kr` 과 `snu.ac.kr` 이 다른 도메인이 되면 안 된다. */
    @Column(nullable = false, length = 255)
    val domain: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
}
