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
    private val collectionService: codekr.api.collection.service.ProblemCollectionService,
    private val userRepository: UserRepository,
    private val profileRepository: UserProfileRepository,
    private val activityService: ActivityService,
    private val scoreRepository: UserProblemScoreRepository,
    private val rankingService: RankingService,
    private val badgeRepository: BadgeRepository,
    private val affiliations: codekr.api.affiliation.repository.AffiliationRepository,
    private val userAffiliations: codekr.api.affiliation.repository.UserAffiliationRepository,
) {

    /**
     * 프로필 (#83). **주소는 `handle` 이다** (#307) — 이름을 바꿔도 링크가 살아 있다.
     *
     * 옛 주소(닉네임)로 들어온 사람을 위해 **닉네임으로도 한 번 더 찾는다.** 링크를
     * 이미 주고받은 사람에게 404 를 보이는 것보다 낫다 — 다만 닉네임은 바뀌므로
     * 그 길은 언젠가 끊긴다.
     */
    fun findByHandle(handle: String, viewerId: Long? = null): UserProfileResponse {
        val user = userRepository.findByHandle(handle)
            ?: userRepository.findByNickname(handle)
            ?: throw ApiException(ErrorCode.USER_NOT_FOUND)
        // 탈퇴한 계정의 프로필은 열리지 않는다. 남은 글에서도 링크가 걸리지 않는다 (#140).
        if (user.isWithdrawn) throw ApiException(ErrorCode.USER_NOT_FOUND)
        // 스트릭은 활동 서비스 한 곳에서 받는다 (#117). 여기서 다시 계산하면 언젠가 어긋난다.
        val streaks = activityService.streaksOf(user.id)
        // 점수는 랭킹과 같은 규칙(상위 N개)으로 센다. 화면마다 다른 숫자가 보이면 안 된다.
        val score = scoreRepository.totalsOf(user.id).first

        return UserProfileResponse(
            nickname = user.nickname,
            handle = user.handle,
            avatarUrl = AvatarService.urlOf(user.avatarKey),
            bio = user.bio,
            collections = collectionService.findPublicOf(user.id, viewerId),
            joinedAt = user.createdAt,
            solvedCount = profileRepository.countSolvedProblems(user.id),
            submissionCount = profileRepository.countSubmissions(user.id),
            solvedByTier = profileRepository.solvedByTier(user.id),
            solvedByTag = profileRepository.solvedByTag(user.id),
            currentStreak = streaks.current,
            longestStreak = streaks.longest,
            score = score,
            // 티어는 **도달했던 최고 점수**로 정한다. 강등이 없으므로 현재 점수와 갈라질 수 있다.
            skillTier = SkillTier.of(user.peakScore)?.let(SkillTierResponse::from),
            // 랭킹은 아직 표시 이름으로 찾는다 — 그 표의 key 를 함께 옮기는 것은 별개다.
            rank = rankingService.rankOf(user.nickname, RankingMetric.SCORE, RankingPeriod.ALL_TIME)?.rank,
            badges = badgeRepository.findAll(user.id),
            // **주소는 담지 않는다** — 남에게 보일 것이 아니다 (#398).
            affiliations = userAffiliations.findByUserIdOrderByIdAsc(user.id).mapNotNull { link ->
                affiliations.findById(link.affiliationId).orElse(null)
                    ?.let { codekr.api.user.dto.ProfileAffiliation(it.name, it.kind.label) }
            },
        )
    }
}
