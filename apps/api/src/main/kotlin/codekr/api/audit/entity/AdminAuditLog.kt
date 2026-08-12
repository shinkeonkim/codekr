package codekr.api.audit.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 어드민이 한 관리 행위 하나 (#225).
 *
 * **덧붙이기만 한다.** 고치거나 지우는 경로를 만들지 않는다 — 감사 기록의 뜻이 거기에
 * 있다. 그래서 이 엔티티에는 `var` 가 없다.
 */
@Entity
@Table(name = "admin_audit_logs")
class AdminAuditLog(
    @Column(name = "actor_id", nullable = false)
    val actorId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val action: AdminAction = AdminAction.ROLE_CHANGE,

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    val targetType: AuditTargetType = AuditTargetType.USER,

    @Column(name = "target_id", nullable = false)
    val targetId: Long = 0,

    /**
     * 그때의 대상 이름.
     *
     * **강제 탈퇴는 닉네임을 지운다** (#140). 사본이 없으면 기록이 "누구를" 지웠는지
     * 말하지 못한다.
     */
    @Column(name = "target_label", length = 100)
    val targetLabel: String? = null,

    @Column(length = 500)
    val reason: String? = null,

    @Column(length = 500)
    val detail: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    val createdAt: Instant = Instant.EPOCH
}

/**
 * 남기는 관리 행위 (#225).
 *
 * **조회는 남기지 않는다.** 남기면 양이 폭발하고 정작 중요한 것이 묻힌다.
 */
enum class AdminAction(val label: String, val requiresReason: Boolean) {
    /** 역할 부여/회수 (#103). */
    ROLE_CHANGE("역할 변경", requiresReason = false),

    /**
     * 강제 탈퇴 (#140). **되돌릴 수 없으므로 사유가 필수다.**
     *
     * 계정이 사라진 뒤에 "누가 왜 지웠는지" 를 물으면 이 값이 유일한 답이다.
     */
    FORCE_WITHDRAW("강제 탈퇴", requiresReason = true),

    /** 랭킹·활동 재계산 (#177, #105). 되돌릴 수 있어 사유를 요구하지 않는다. */
    RECOMPUTE("재계산", requiresReason = false),
}

enum class AuditTargetType { USER }
