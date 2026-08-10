package codekr.api.notification.dto

import codekr.api.notification.entity.Notification
import codekr.api.notification.entity.NotificationCategory
import java.time.Instant

data class NotificationResponse(
    val id: Long,
    val category: NotificationCategory,
    val categoryLabel: String,
    val title: String,
    val body: String?,
    val link: String?,
    val read: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(notification: Notification) = NotificationResponse(
            id = notification.id,
            category = notification.category,
            categoryLabel = notification.category.label,
            title = notification.title,
            body = notification.body,
            link = notification.link,
            read = notification.isRead,
            createdAt = notification.createdAt,
        )
    }
}
