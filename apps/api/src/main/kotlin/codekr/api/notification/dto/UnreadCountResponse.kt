package codekr.api.notification.dto

import codekr.api.notification.entity.NotificationCategory

/**
 * 헤더의 미읽음 뱃지. 목록 전체를 받지 않고 숫자만 자주 확인한다.
 *
 * [byCategory] 는 탭마다 안 읽은 수다 (#135). 헤더는 총합만 쓰지만, 목록 화면이
 * 탭 배지를 그리려고 같은 것을 또 부르지 않게 함께 내린다.
 */
data class UnreadCountResponse(
    val unreadCount: Long,
    val byCategory: Map<NotificationCategory, Long> = emptyMap(),
)
