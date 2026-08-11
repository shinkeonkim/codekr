package codekr.api.notification.repository

import codekr.api.notification.entity.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface NotificationRepository : JpaRepository<Notification, Long> {

    fun findByUserIdOrderByIdDesc(userId: Long, pageable: Pageable): Page<Notification>

    fun findByUserIdAndReadAtIsNullOrderByIdDesc(userId: Long, pageable: Pageable): Page<Notification>

    fun countByUserIdAndReadAtIsNull(userId: Long): Long

    /**
     * 모두 읽음. 한 건씩 불러 고치면 알림이 쌓인 사용자에게 느리다.
     *
     * 이미 읽은 것은 건드리지 않는다 — 처음 읽은 시각을 덮으면 안 된다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    fun markAllRead(@Param("userId") userId: Long, @Param("now") now: Instant): Int

    fun findByIdAndUserId(id: Long, userId: Long): Notification?
}
