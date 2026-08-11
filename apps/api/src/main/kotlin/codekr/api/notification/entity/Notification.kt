package codekr.api.notification.entity

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
 * 사용자에게 남기는 알림 (#106).
 *
 * 토스트(#112)와 다르다 — 토스트는 내가 방금 한 행동의 결과이고, 이것은 **내가 없을 때
 * 서버에서 일어난 일**이다. 그래서 서버에 남고 읽음 처리를 한다.
 */
@Entity
@Table(name = "notifications")
class Notification(

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val category: NotificationCategory,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(columnDefinition = "text")
    val body: String? = null,

    /** 누르면 갈 경로. 같은 출처의 경로만 담는다. */
    @Column(length = 500)
    val link: String? = null,

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "read_at")
    var readAt: Instant? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    val isRead: Boolean get() = readAt != null

    /** 이미 읽은 것을 다시 읽어도 시각을 덮지 않는다 — 처음 읽은 때가 정보다. */
    fun markRead(now: Instant = Instant.now()) {
        if (readAt == null) readAt = now
    }
}
