package codekr.api.user.dto

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
)
