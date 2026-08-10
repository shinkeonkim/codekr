package codekr.api.notification.dto

/** 헤더의 미읽음 뱃지. 목록 전체를 받지 않고 숫자만 자주 확인한다. */
data class UnreadCountResponse(val unreadCount: Long)
