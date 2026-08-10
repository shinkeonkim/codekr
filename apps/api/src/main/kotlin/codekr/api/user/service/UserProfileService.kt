package codekr.api.user.service

import codekr.api.activity.repository.ActivityRepository
import codekr.api.activity.service.StreakCalculator
import codekr.api.activity.ActivityPolicy
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.user.dto.UserProfileResponse
import codekr.api.user.repository.UserProfileRepository
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 공개 프로필 (#83).
 *
 * **닉네임으로 찾는다.** 주소가 읽히는 편이 훨씬 쓸모 있고, 지금은 닉네임을 바꿀 수 없다.
 * 닉네임 변경을 지원하게 되면 옛 주소를 어떻게 할지(리다이렉트/차단) 그때 정한다.
 */
@Service
@Transactional(readOnly = true)
class UserProfileService(
    private val userRepository: UserRepository,
    private val profileRepository: UserProfileRepository,
    private val activityRepository: ActivityRepository,
) {

    fun findByNickname(nickname: String): UserProfileResponse {
        val user = userRepository.findByNickname(nickname) ?: throw ApiException(ErrorCode.USER_NOT_FOUND)
        val activeDates = activityRepository.findActiveDates(user.id)
        val today = LocalDate.now(ActivityPolicy.ZONE)

        return UserProfileResponse(
            nickname = user.nickname,
            joinedAt = user.createdAt,
            solvedCount = profileRepository.countSolvedProblems(user.id),
            submissionCount = profileRepository.countSubmissions(user.id),
            solvedByTier = profileRepository.solvedByTier(user.id),
            currentStreak = StreakCalculator.current(activeDates, today),
            longestStreak = StreakCalculator.longest(activeDates),
        )
    }
}
