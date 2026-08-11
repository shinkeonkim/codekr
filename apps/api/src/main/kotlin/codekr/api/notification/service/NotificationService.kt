package codekr.api.notification.service

import codekr.api.common.dto.PageResponse
import codekr.api.notification.dto.NotificationResponse
import codekr.api.notification.entity.Notification
import codekr.api.notification.entity.NotificationCategory
import codekr.api.notification.repository.NotificationMuteRepository
import codekr.api.notification.repository.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 웹 내 알림 (#106).
 *
 * 알림을 만드는 쪽(재채점·대회 공지)은 이 서비스만 부른다. 수신 거부 확인과 저장 방식은
 * 여기 한 곳에 둔다 — 부르는 곳마다 확인하면 언젠가 빠뜨린다.
 */
@Service
@Transactional(readOnly = true)
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val muteRepository: NotificationMuteRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 한 사람에게 알린다. 수신을 껐으면 **아무것도 만들지 않는다.**
     *
     * 만들어 두고 감추는 방식이 아닌 이유: 나중에 다시 켰을 때 그동안의 알림이 쏟아진다.
     * 껐던 기간의 일은 이미 지나간 일이다.
     */
    @Transactional
    fun notify(
        userId: Long,
        category: NotificationCategory,
        title: String,
        body: String? = null,
        link: String? = null,
    ): Boolean {
        if (category in muteRepository.findMuted(userId)) return false
        notificationRepository.save(Notification(userId, category, title, body, link))
        return true
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

        val muted = muteRepository.findMutedBy(userIds, category)
        val targets = userIds.distinct().filterNot { it in muted }
        notificationRepository.saveAll(
            targets.map { Notification(it, category, title, body, link(it)) },
        )

        log.info("알림 {}건 생성: category={} (수신 거부 {}명 제외)", targets.size, category, muted.size)
        return targets.size
    }

    fun findPage(userId: Long, unreadOnly: Boolean, pageable: Pageable): PageResponse<NotificationResponse> {
        val page = if (unreadOnly) {
            notificationRepository.findByUserIdAndReadAtIsNullOrderByIdDesc(userId, pageable)
        } else {
            notificationRepository.findByUserIdOrderByIdDesc(userId, pageable)
        }
        return PageResponse.from(page.map(NotificationResponse::from))
    }

    fun unreadCount(userId: Long): Long = notificationRepository.countByUserIdAndReadAtIsNull(userId)

    @Transactional
    fun markRead(userId: Long, id: Long) {
        notificationRepository.findByIdAndUserId(id, userId)?.markRead()
    }

    @Transactional
    fun markAllRead(userId: Long): Int = notificationRepository.markAllRead(userId, Instant.now())
}
