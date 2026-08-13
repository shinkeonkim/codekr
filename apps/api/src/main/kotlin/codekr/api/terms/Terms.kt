package codekr.api.terms

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

enum class TermKind(val label: String) {
    SERVICE("서비스 이용약관"),
    PRIVACY("개인정보 처리방침"),
}

/**
 * 약관 한 판 (#235).
 *
 * **버전을 데이터로 둔다.** 문서를 파일로만 두면 개정 이력이 git 에만 남고, 누가 어느
 * 버전에 동의했는지 알 수 없다.
 */
@Entity
@Table(name = "term_documents")
class TermDocument(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val kind: TermKind,

    @Column(nullable = false, length = 20)
    val version: String,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(nullable = false)
    val body: String,

    /** 미래면 아직 받지 않는다 — 개정을 미리 넣어 두고 날짜에 맞춰 켜기 위해서다. */
    @Column(name = "effective_at", nullable = false)
    val effectiveAt: Instant,

    @Column(nullable = false)
    val required: Boolean = true,

    /**
     * 기존 회원에게 **다시 받아야 하는** 개정인가.
     *
     * 오타를 고칠 때마다 모든 회원을 막으면 개정 자체를 안 하게 된다. 그 판단은 사람이 한다.
     */
    @Column(nullable = false)
    val reconsent: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
}

/** 누가, 어느 판에, 언제 동의했는가 (#235). */
@Entity
@Table(name = "term_agreements")
class TermAgreement(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "document_id", nullable = false)
    val documentId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "agreed_at", nullable = false, updatable = false)
    val agreedAt: Instant = Instant.now()
}

interface TermDocumentRepository : JpaRepository<TermDocument, Long> {

    /**
     * 지금 시행 중인 판들 — 종류마다 하나.
     *
     * 같은 종류의 여러 판 중 **시행일이 지난 것 가운데 가장 최신**이다. 미래 시행일은
     * 아직 없는 것으로 본다.
     */
    @Query(
        """
        SELECT d FROM TermDocument d
        WHERE d.effectiveAt <= :now
          AND d.effectiveAt = (
            SELECT max(x.effectiveAt) FROM TermDocument x
            WHERE x.kind = d.kind AND x.effectiveAt <= :now
          )
        ORDER BY d.kind
        """,
    )
    fun findEffective(@Param("now") now: Instant): List<TermDocument>
}

interface TermAgreementRepository : JpaRepository<TermAgreement, Long> {

    fun findByUserId(userId: Long): List<TermAgreement>

    fun existsByUserIdAndDocumentId(userId: Long, documentId: Long): Boolean
}
