package codekr.api.notification.repository

import codekr.api.notification.entity.NotificationCategory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 수신을 끈 카테고리 (#106).
 *
 * **끈 것만 저장한다.** 전체 조합을 저장하면 카테고리가 늘 때마다 모든 사용자 행을
 * 채워야 하고, 그 사이 가입한 사용자는 빠진다.
 */
@Repository
class NotificationMuteRepository(private val jdbcClient: JdbcClient) {

    fun findMuted(userId: Long): Set<NotificationCategory> =
        jdbcClient.sql("SELECT category FROM notification_mutes WHERE user_id = :userId")
            .param("userId", userId)
            .query { rs, _ -> NotificationCategory.valueOf(rs.getString("category")) }
            .list()
            .toSet()

    /** 여러 사용자의 수신 거부를 한 번에 읽는다. 대량 발송이 사용자마다 질의하지 않게. */
    fun findMutedBy(userIds: Collection<Long>, category: NotificationCategory): Set<Long> {
        if (userIds.isEmpty()) return emptySet()
        return jdbcClient.sql(
            "SELECT user_id FROM notification_mutes WHERE category = :category AND user_id IN (:userIds)",
        )
            .param("category", category.name)
            .param("userIds", userIds)
            .query { rs, _ -> rs.getLong("user_id") }
            .list()
            .toSet()
    }

    fun replaceMuted(userId: Long, categories: Set<NotificationCategory>) {
        jdbcClient.sql("DELETE FROM notification_mutes WHERE user_id = :userId")
            .param("userId", userId)
            .update()

        categories.filter { it.mutable }.forEach { category ->
            jdbcClient.sql(
                "INSERT INTO notification_mutes (user_id, category) VALUES (:userId, :category)",
            )
                .param("userId", userId)
                .param("category", category.name)
                .update()
        }
    }
}
