package codekr.api.user.dto

import codekr.api.notification.entity.NotificationCategory
import codekr.api.submission.entity.SubmissionVisibility
import codekr.api.user.entity.User

/**
 * 내 설정 (#104).
 *
 * **알림을 끄는 항목은 없다** (#199). 카테고리 목록은 그대로 내려준다 — 알림 목록의
 * 탭(#135)이 이것으로 탭을 만든다. 지우면 화면이 카테고리를 하드코딩하게 된다.
 */
data class UserSettingsResponse(
    val defaultSubmissionVisibility: SubmissionVisibility,
    /** 알림 목록의 탭 이름. 화면이 목록을 하드코딩하지 않게 서버가 알려준다 (#135). */
    val notificationCategories: List<NotificationCategoryOption>,
) {
    companion object {
        fun of(user: User) = UserSettingsResponse(
            defaultSubmissionVisibility = user.defaultSubmissionVisibility,
            notificationCategories = NotificationCategory.entries.map {
                NotificationCategoryOption(it, it.label)
            },
        )
    }
}

data class NotificationCategoryOption(val category: NotificationCategory, val label: String)
