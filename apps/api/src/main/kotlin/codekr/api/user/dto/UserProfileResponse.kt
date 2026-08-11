package codekr.api.user.dto

import codekr.api.ranking.badge.AwardedBadge
import codekr.api.ranking.entity.SkillTier
import java.time.Instant

/**
 * 공개 프로필 (#83).
 *
 * **이미 공개된 것만 모은다.** 전체 제출 목록(#34)이 누가 어떤 문제를 언제 내서 어떤
 * 결과를 받았는지 이미 공개하므로, 이 화면은 그것을 사람 기준으로 묶은 것이다.
 * 이메일처럼 목록에 없던 것은 담지 않는다.
 */
data class UserProfileResponse(
    val nickname: String,
    val joinedAt: Instant,
    /** 푼 문제 수. 제출 수가 아니라 문제 수다. */
    val solvedCount: Int,
    val submissionCount: Int,
    val solvedByTier: List<SolvedByTier>,
    /** 전체 기간 기준 (#81). */
    val currentStreak: Int,
    val longestStreak: Int,
    /**
     * 랭킹 점수와 실력 티어 (#57, #58).
     *
     * **문제 난이도 티어와 다른 개념이다.** 화면은 "실력 티어"로 표기해 `solvedByTier`
     * (문제 난이도별 해결 수)와 구분한다.
     */
    val score: Int,
    val skillTier: SkillTierResponse?,
    /** 랭킹을 껐거나 아직 푼 문제가 없으면 null — 꼴찌가 아니라 순위가 없는 것이다. */
    val rank: Int?,
    val badges: List<AwardedBadge>,
)

/** 실력 티어 (#58). 다음 티어까지 얼마가 남았는지 함께 준다 — 없으면 숫자가 목표가 되지 못한다. */
data class SkillTierResponse(val level: Int, val name: String, val nextLevelScore: Int?) {
    companion object {
        fun from(tier: SkillTier) = SkillTierResponse(tier.level, tier.name, tier.nextLevelScore)
    }
}
