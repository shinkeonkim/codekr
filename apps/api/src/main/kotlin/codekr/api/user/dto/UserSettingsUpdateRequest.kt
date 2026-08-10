package codekr.api.user.dto

import codekr.api.notification.entity.NotificationCategory
import codekr.api.submission.entity.SubmissionVisibility

/**
 * 설정 변경 (#104).
 *
 * 값이 null 인 항목은 **바꾸지 않는다.** 항목이 늘어도 화면이 전체를 보내지 않아도 되게
 * 하기 위함이다 — 전체를 보내게 하면 새 항목이 생길 때 옛 화면이 그것을 지운다.
 */
data class UserSettingsUpdateRequest(
    val defaultSubmissionVisibility: SubmissionVisibility? = null,
    /** 랭킹 비참여 (#58). */
    val rankingOptOut: Boolean? = null,
    /** 수신을 끌 카테고리 전체. 부분 수정이 아니라 통째로 교체한다. */
    val mutedNotificationCategories: Set<NotificationCategory>? = null,
)
