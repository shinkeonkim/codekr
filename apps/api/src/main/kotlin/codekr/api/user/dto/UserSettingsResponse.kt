package codekr.api.user.dto

import codekr.api.notification.entity.NotificationCategory
import codekr.api.submission.entity.SubmissionVisibility
import codekr.api.user.entity.User

/**
 * 내 설정 (#104, #106).
 *
 * 알림은 **끈 것만** 담는다 (`mutedNotificationCategories`). 화면은 전체 카테고리 목록과
 * 대조해 켜짐/꺼짐을 그린다 — 카테고리가 늘어도 서버가 사용자 행을 채울 필요가 없다.
 */
data class UserSettingsResponse(
    val defaultSubmissionVisibility: SubmissionVisibility,
    /** true 면 랭킹 목록에 나오지 않는다 (#58). 점수는 계속 쌓인다. */
    val rankingOptOut: Boolean,
    /**
     * 내 공개 코드를 누가 읽었는지 알림받을지 (#136). **기본은 끔.**
     *
     * 꺼져 있으면 아예 기록하지 않는다 — 켜는 것이 곧 추적에 대한 동의다.
     */
    val viewNotificationEnabled: Boolean,
    val mutedNotificationCategories: Set<NotificationCategory>,
    /** 끌 수 있는 카테고리와 이름. 화면이 목록을 하드코딩하지 않게 서버가 알려준다. */
    val notificationCategories: List<NotificationCategoryOption>,
) {
    companion object {
        fun of(user: User, muted: Set<NotificationCategory>) = UserSettingsResponse(
            defaultSubmissionVisibility = user.defaultSubmissionVisibility,
            rankingOptOut = user.rankingOptOut,
            viewNotificationEnabled = user.viewNotificationEnabled,
            mutedNotificationCategories = muted,
            notificationCategories = NotificationCategory.entries.map {
                NotificationCategoryOption(it, it.label, it.mutable)
            },
        )
    }
}

data class NotificationCategoryOption(
    val category: NotificationCategory,
    val label: String,
    /** false 면 화면이 끄기 스위치를 보여주지 않는다 (시스템 공지). */
    val mutable: Boolean,
)
