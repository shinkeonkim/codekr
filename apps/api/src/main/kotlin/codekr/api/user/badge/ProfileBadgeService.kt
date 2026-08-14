package codekr.api.user.badge

import codekr.api.common.error.ApiException
import codekr.api.user.service.UserProfileService
import org.springframework.stereotype.Service

/**
 * 배지에 그릴 값을 프로필에서 그대로 가져온다 (#475).
 *
 * **새로 계산하지 않는다.** 화면이 보는 숫자와 배지의 숫자가 다르면, 둘 중 어느 것이
 * 맞는지 아무도 모르게 된다 — 그 순간 배지는 광고가 아니라 오해가 된다.
 */
@Service
class ProfileBadgeService(private val userProfileService: UserProfileService) {

    fun render(handle: String, theme: BadgeTheme): String {
        val profile = try {
            userProfileService.findByHandle(handle)
        } catch (_: ApiException) {
            // 없는 사람과 탈퇴한 사람은 **같은 그림**이다 (#140).
            // 다르게 주면 "이 handle 은 있었다" 가 새어 나간다.
            return ProfileBadgeSvg.unknown(theme)
        }

        return ProfileBadgeSvg.of(
            ProfileBadgeSvg.Data(
                handle = profile.handle,
                tierName = profile.skillTier?.name,
                score = profile.score,
                solvedCount = profile.solvedCount,
                streak = profile.currentStreak,
            ),
            theme,
        )
    }
}
