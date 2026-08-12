package codekr.api.user.suspension

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
 * 무엇을 막는가 (#224).
 *
 * **"정지" 라고만 하면 범위가 흐리다.** 댓글 스팸 때문에 문제 풀이까지 막을 이유는
 * 없고, 반대로 채점기를 괴롭히는 계정이라면 제출만 막으면 된다.
 *
 * **읽기는 어떤 값으로도 막지 않는다.** 로그아웃하면 그대로 보이므로 막는 시늉일
 * 뿐이고, 자기가 왜 막혔는지 읽을 길까지 없애게 된다.
 */
enum class SuspensionScope(val label: String) {
    /** 글·댓글·문제집 등 남에게 보이는 것을 만들거나 고치는 일. */
    WRITE("쓰기"),

    /** 코드를 내고 채점받는 일. */
    SUBMIT("제출"),

    ALL("쓰기·제출"),
    ;

    fun covers(other: SuspensionScope): Boolean = this == ALL || this == other
}

/**
 * 한 번의 정지 (#224).
 *
 * **행을 고쳐 상태를 바꾸지 않는다** — 정지와 해제가 각각 언제 누구에 의해 일어났는지가
 * 그대로 남아야 한다.
 *
 * 지금 효력이 있는지는 **여기서 계산하지 않는다.** 그 판단은 `findActive` 의 질의
 * 하나에만 둔다 — 같은 규칙이 두 곳에 있으면 갈린다.
 */
@Entity
@Table(name = "user_suspensions")
class UserSuspension(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val scope: SuspensionScope,

    @Column(nullable = false)
    val reason: String,

    /** null 이면 기한이 없다. 어드민이 풀기 전까지 이어진다. */
    @Column(name = "ends_at")
    val endsAt: Instant? = null,

    @Column(name = "created_by")
    val createdBy: Long? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "lifted_at")
    var liftedAt: Instant? = null
        protected set

    @Column(name = "lifted_by")
    var liftedBy: Long? = null
        protected set

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    fun lift(actorId: Long, now: Instant) {
        if (liftedAt != null) return
        liftedAt = now
        liftedBy = actorId
    }
}
