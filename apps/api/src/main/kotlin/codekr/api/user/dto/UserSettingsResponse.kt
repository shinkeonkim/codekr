package codekr.api.user.dto

import codekr.api.notification.entity.NotificationCategory
import codekr.api.submission.entity.SubmissionVisibility
import codekr.api.user.entity.User
import codekr.api.user.entity.UserTheme

/**
 * 내 설정 (#104).
 *
 * **알림을 끄는 항목은 없다** (#199). 카테고리 목록은 그대로 내려준다 — 알림 목록의
 * 탭(#135)이 이것으로 탭을 만든다. 지우면 화면이 카테고리를 하드코딩하게 된다.
 */
data class UserSettingsResponse(
    val defaultSubmissionVisibility: SubmissionVisibility,
    /**
     * 고른 화면 테마 (#274). **`null` 이면 이 기기의 선택을 그대로 쓴다.**
     *
     * 기기에도 남는 값이라(#206) 서버가 아무것도 모를 수 있다 — 그때 화면이
     * 자기 값으로 계속 도는 것이 맞다.
     */
    val theme: UserTheme?,
    /** 알림 목록의 탭 이름. 화면이 목록을 하드코딩하지 않게 서버가 알려준다 (#135). */
    val notificationCategories: List<NotificationCategoryOption>,
) {
    companion object {
        fun of(user: User) = UserSettingsResponse(
            defaultSubmissionVisibility = user.defaultSubmissionVisibility,
            theme = user.theme,
            notificationCategories = NotificationCategory.entries.map {
                NotificationCategoryOption(it, it.label)
            },
        )
    }
}

data class NotificationCategoryOption(val category: NotificationCategory, val label: String)
