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

    /**
     * 회원 정지 (#224). **남에게 보이는 조치라 사유가 필수다** — 정지된 사람이 막힌
     * 행동을 하면 이 사유를 그대로 읽는다.
     */
    SUSPEND("정지", requiresReason = true),

    /**
     * 프로필 소개 지우기 (#310). **남에게 보이는 것을 지우는 일이라 사유가 필수다.**
     *
     * 지운 뒤에는 원래 무엇이 적혀 있었는지 알 길이 없다 — 사유가 유일한 설명이다.
     */
    BIO_CLEAR("소개 지우기", requiresReason = true),

    /**
     * 공개 문제집 내리기 (#208). **남에게 보이는 것을 내리는 일이라 사유가 필수다** —
     * 주인에게 그 사유가 그대로 전해진다.
     */
    COLLECTION_TAKEDOWN("문제집 내리기", requiresReason = true),

    /**
     * 남의 글·댓글 내리기 (#336). **사유가 필수다** — 남이 쓴 것을 지우는 일이고,
     * 지운 뒤에는 무엇이었는지 알 길이 없다.
     */
    POST_DELETE("글 삭제", requiresReason = true),
    COMMENT_DELETE("댓글 삭제", requiresReason = true),

    /**
     * 그룹 해산 (#438). **사유가 필수다** — 남이 만든 것을 없애는 일이고, 멤버 전원에게
     * 그 사유가 그대로 전해진다.
     *
     * 문제집 내리기(#208)와 다른 점: 그룹은 "비공개로 되돌리기" 가 없다. 초대 링크로
     * 들어오는 곳이라 공개 여부가 존재 여부와 같다.
     */
    GROUP_TAKEDOWN("그룹 해산", requiresReason = true),

    /**
     * 대회 참가 승인·거절 (#466).
     *
     * **사유는 거절에만 필수다.** 승인은 신청한 것을 받아 주는 일이라 설명할 것이 없고,
     * 거절은 **행을 지우므로** 그 사유가 유일한 설명이 된다.
     */
    CONTEST_APPROVAL("대회 참가 승인", requiresReason = false),

    /** 정지 해제 (#224). 되돌리는 쪽이라 사유를 요구하지 않는다. */
    LIFT_SUSPENSION("정지 해제", requiresReason = false),

    /** 랭킹·활동 재계산 (#177, #105). 되돌릴 수 있어 사유를 요구하지 않는다. */
    RECOMPUTE("재계산", requiresReason = false),

    /**
     * 지문에서 초안 만들기 (#230).
     *
     * 아무것도 바꾸지 않는데 기록에 남기는 이유: **바깥으로 나가고 돈이 드는 호출**이다.
     * 나중에 "누가 몇 번 불렀나" 를 물을 자리가 있어야 한다. 사유는 요구하지 않는다 —
     * 되돌릴 것이 없고, 매번 적게 하면 "확인" 같은 값만 쌓인다.
     */
    PROBLEM_DRAFT("문제 초안 만들기", requiresReason = false),

    /**
     * 실행기·채점기 조정 (#390).
     *
     * 사유를 요구하지 않는다 — 되돌릴 수 있고, 큐가 밀릴 때 급히 누르는 일이다.
     * **누가 무엇을 몇으로 바꿨는지**는 남는다. 전에는 그것도 없었다.
     */
    SCALE("워크로드 조정", requiresReason = false),

    /**
     * 소속·도메인 변경 (#397).
     *
     * 사유를 요구하지 않는다 — 목록을 채우는 일이라 매번 적게 하면 "추가" 같은 값만 쌓인다.
     * 다만 **잘못 넣으면 그 도메인을 가진 모두가 그 소속을 얻으므로** 누가 무엇을
     * 했는지는 반드시 남는다.
     */
    AFFILIATION_CHANGE("소속 변경", requiresReason = false),
}

enum class AuditTargetType { USER }
