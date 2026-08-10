package codekr.api.user.dto

import codekr.api.submission.entity.SubmissionVisibility
import codekr.api.user.entity.User

/**
 * 내 설정 (#104).
 *
 * 지금은 항목이 하나지만 **앞으로 들어올 것들의 자리**이기도 하다 —
 * 알림 카테고리별 수신 설정(#106)이 여기 붙는다.
 */
data class UserSettingsResponse(
    val defaultSubmissionVisibility: SubmissionVisibility,
) {
    companion object {
        fun from(user: User) = UserSettingsResponse(user.defaultSubmissionVisibility)
    }
}
