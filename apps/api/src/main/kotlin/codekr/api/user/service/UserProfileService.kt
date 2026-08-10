package codekr.api.user.service

import codekr.api.activity.service.ActivityService
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.ranking.badge.BadgeRepository
import codekr.api.ranking.entity.RankingMetric
import codekr.api.ranking.entity.RankingPeriod
import codekr.api.ranking.entity.SkillTier
import codekr.api.ranking.repository.UserProblemScoreRepository
import codekr.api.ranking.service.RankingService
import codekr.api.user.avatar.AvatarService
import codekr.api.user.dto.SkillTierResponse
import codekr.api.user.dto.UserProfileResponse
import codekr.api.user.repository.UserProfileRepository
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
    private val activityService: ActivityService,
    private val scoreRepository: UserProblemScoreRepository,
    private val rankingService: RankingService,
    private val badgeRepository: BadgeRepository,
) {

    fun findByNickname(nickname: String): UserProfileResponse {
        val user = userRepository.findByNickname(nickname) ?: throw ApiException(ErrorCode.USER_NOT_FOUND)
        // 스트릭은 활동 서비스 한 곳에서 받는다 (#117). 여기서 다시 계산하면 언젠가 어긋난다.
        val streaks = activityService.streaksOf(user.id)
        // 점수는 랭킹과 같은 규칙(상위 N개)으로 센다. 화면마다 다른 숫자가 보이면 안 된다.
        val score = scoreRepository.totalsOf(user.id).first

        return UserProfileResponse(
            nickname = user.nickname,
            avatarUrl = AvatarService.urlOf(user.avatarKey),
            joinedAt = user.createdAt,
            solvedCount = profileRepository.countSolvedProblems(user.id),
            submissionCount = profileRepository.countSubmissions(user.id),
            solvedByTier = profileRepository.solvedByTier(user.id),
            currentStreak = streaks.current,
            longestStreak = streaks.longest,
            score = score,
            // 티어는 **도달했던 최고 점수**로 정한다. 강등이 없으므로 현재 점수와 갈라질 수 있다.
            skillTier = SkillTier.of(user.peakScore)?.let(SkillTierResponse::from),
            rank = rankingService.rankOf(nickname, RankingMetric.SCORE, RankingPeriod.ALL_TIME)?.rank,
            badges = badgeRepository.findAll(user.id),
        )
    }
}
