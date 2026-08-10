package codekr.api.notification.repository

import codekr.api.notification.entity.Notification
import codekr.api.notification.entity.NotificationCategory
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

    fun findByUserIdAndCategoryOrderByIdDesc(
        userId: Long,
        category: NotificationCategory,
        pageable: Pageable,
    ): Page<Notification>

    fun findByUserIdAndCategoryAndReadAtIsNullOrderByIdDesc(
        userId: Long,
        category: NotificationCategory,
        pageable: Pageable,
    ): Page<Notification>

    fun countByUserIdAndReadAtIsNull(userId: Long): Long

    /** 탭마다 안 읽은 수를 보여주려면 한 번에 세어야 한다. 탭 수만큼 질의하지 않는다. */
    @Query(
        """
        SELECT n.category, count(n)
        FROM Notification n
        WHERE n.userId = :userId AND n.readAt IS NULL
        GROUP BY n.category
        """,
    )
    fun countUnreadByCategory(@Param("userId") userId: Long): List<Array<Any>>

    /**
     * 모두 읽음. 한 건씩 불러 고치면 알림이 쌓인 사용자에게 느리다.
     *
     * 이미 읽은 것은 건드리지 않는다 — 처음 읽은 시각을 덮으면 안 된다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    fun markAllRead(@Param("userId") userId: Long, @Param("now") now: Instant): Int

    /**
     * 한 카테고리만 읽음 처리 (#135).
     *
     * **보고 있는 탭만 읽는다.** 채점 탭에서 눌렀는데 대회 알림까지 읽음이 되면
     * 보지 않은 것을 읽은 것으로 만든 셈이고, 되돌릴 수 없다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE Notification n SET n.readAt = :now
        WHERE n.userId = :userId AND n.category = :category AND n.readAt IS NULL
        """,
    )
    fun markCategoryRead(
        @Param("userId") userId: Long,
        @Param("category") category: NotificationCategory,
        @Param("now") now: Instant,
    ): Int

    fun findByIdAndUserId(id: Long, userId: Long): Notification?
}
