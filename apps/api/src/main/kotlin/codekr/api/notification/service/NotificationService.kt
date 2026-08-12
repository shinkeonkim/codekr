package codekr.api.notification.service

import codekr.api.common.dto.PageResponse
import codekr.api.notification.dto.NotificationResponse
import codekr.api.notification.entity.Notification
import codekr.api.notification.entity.NotificationCategory
import codekr.api.notification.repository.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 웹 내 알림 (#106).
 *
 * 알림을 만드는 쪽(재채점·대회 공지)은 이 서비스만 부른다. 저장 방식은 여기 한 곳에 둔다.
 */
@Service
@Transactional(readOnly = true)
class NotificationService(private val notificationRepository: NotificationRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 한 사람에게 알린다.
     *
     * **끄는 수단이 없다** (#199). 사용자 설정과 무관하게 항상 만들어진다 — 끌 수 있게
     * 두었더니 끈 동안의 알림이 아예 만들어지지 않아 되돌릴 수 없었다.
     */
    @Transactional
    fun notify(
        userId: Long,
        category: NotificationCategory,
        title: String,
        body: String? = null,
        link: String? = null,
    ) {
        notificationRepository.save(Notification(userId, category, title, body, link))
    }

    /**
     * 여러 사람에게 한 번에 알린다 (#107 재채점이 이걸 쓴다).
     *
     * 재채점 한 번에 수천 명분이 생길 수 있다. 사용자마다 수신 거부를 확인하면
     * 그 수만큼 질의가 나가므로 한 번에 모아 읽는다.
     */
    @Transactional
    fun notifyAll(
        userIds: Collection<Long>,
        category: NotificationCategory,
        title: String,
        body: String? = null,
        link: (Long) -> String? = { null },
    ): Int {
        if (userIds.isEmpty()) return 0

        val targets = userIds.distinct()
        notificationRepository.saveAll(
            targets.map { Notification(it, category, title, body, link(it)) },
        )

        log.info("알림 {}건 생성: category={}", targets.size, category)
        return targets.size
    }

    /**
     * @param category null 이면 전체 탭이다.
     *   "안 읽은 것만" 과 함께 걸면 **교집합**이다 — 이 탭에서 안 읽은 것.
     */
    fun findPage(
        userId: Long,
        unreadOnly: Boolean,
        category: NotificationCategory?,
        pageable: Pageable,
    ): PageResponse<NotificationResponse> {
        val page = when {
            category != null && unreadOnly ->
                notificationRepository.findByUserIdAndCategoryAndReadAtIsNullOrderByIdDesc(userId, category, pageable)
            category != null ->
                notificationRepository.findByUserIdAndCategoryOrderByIdDesc(userId, category, pageable)
            unreadOnly -> notificationRepository.findByUserIdAndReadAtIsNullOrderByIdDesc(userId, pageable)
            else -> notificationRepository.findByUserIdOrderByIdDesc(userId, pageable)
        }
        return PageResponse.from(page.map(NotificationResponse::from))
    }

    fun unreadCount(userId: Long): Long = notificationRepository.countByUserIdAndReadAtIsNull(userId)

    /** 탭마다 안 읽은 수. 없는 카테고리는 0 으로 채워 화면이 빈 값을 다루지 않게 한다. */
    fun unreadCountByCategory(userId: Long): Map<NotificationCategory, Long> {
        val counted = notificationRepository.countUnreadByCategory(userId)
            .associate { it[0] as NotificationCategory to it[1] as Long }
        return NotificationCategory.entries.associateWith { counted[it] ?: 0L }
    }

    @Transactional
    fun markRead(userId: Long, id: Long) {
        notificationRepository.findByIdAndUserId(id, userId)?.markRead()
    }

    /**
     * 모두 읽음. **보고 있는 탭만 읽는다** (#135).
     *
     * 전체 탭에서만 전부 읽는다. 채점 탭에서 눌렀는데 대회 알림까지 읽음이 되면
     * 보지 않은 것을 읽은 것으로 만든 셈이고, 되돌릴 수 없다.
     */
    @Transactional
    fun markAllRead(userId: Long, category: NotificationCategory? = null): Int {
        val now = Instant.now()
        return category
            ?.let { notificationRepository.markCategoryRead(userId, it, now) }
            ?: notificationRepository.markAllRead(userId, now)
    }
}
