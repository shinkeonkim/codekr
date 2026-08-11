package codekr.api.ranking.badge

import codekr.api.activity.service.ActivityService
import codekr.api.problem.entity.ProblemCategory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 정답 하나가 확정될 때마다 조건을 확인해 뱃지를 준다 (#58).
 *
 * **조건은 매번 원자료에서 다시 확인한다.** 카운터를 따로 두면 재채점·삭제로 어긋나고,
 * 어긋난 카운터는 되돌릴 방법이 없다 (#57 의 점수와 같은 이유).
 */
@Component
class BadgeAwarder(
    private val badgeRepository: BadgeRepository,
    private val activityService: ActivityService,
    private val jdbcClient: JdbcClient,
) {

    @Transactional
    fun onAccepted(userId: Long, problemId: Long) {
        badgeRepository.award(userId, Badge.FIRST_ACCEPT.code)

        if (isFirstSolver(userId, problemId)) badgeRepository.award(userId, Badge.FIRST_SOLVER.code)
        categoryReached(userId, problemId)?.let { badgeRepository.award(userId, it) }

        awardStreakBadges(userId)
    }

    /** 스트릭 뱃지는 활동 집계에서 본다 (#105). 제출을 다시 훑지 않는다. */
    @Transactional
    fun awardStreakBadges(userId: Long) {
        val longest = activityService.streaksOf(userId).longest
        if (longest >= 7) badgeRepository.award(userId, Badge.STREAK_7.code)
        if (longest >= 30) badgeRepository.award(userId, Badge.STREAK_30.code)
    }

    /**
     * 그 문제를 가장 먼저 맞혔는지.
     *
     * 이미 점수 표에 기록된 뒤에 부르므로 자기 자신도 후보에 들어간다 — 그래서
     * "나보다 이른 사람이 없다" 로 확인한다.
     */
    private fun isFirstSolver(userId: Long, problemId: Long): Boolean =
        jdbcClient.sql(
            """
            SELECT NOT EXISTS (
                SELECT 1 FROM user_problem_scores other
                WHERE other.problem_id = :problemId
                  AND other.user_id <> :userId
                  AND other.solved_at <= (
                      SELECT mine.solved_at FROM user_problem_scores mine
                      WHERE mine.problem_id = :problemId AND mine.user_id = :userId
                  )
            )
            """,
        )
            .param("problemId", problemId)
            .param("userId", userId)
            .query(Boolean::class.java)
            .optional()
            .orElse(false)

    /** 방금 맞힌 문제의 카테고리에서 기준을 넘었으면 그 뱃지 코드. */
    private fun categoryReached(userId: Long, problemId: Long): String? =
        jdbcClient.sql(
            """
            SELECT p.category
            FROM user_problem_scores s
            JOIN problems p ON p.id = s.problem_id
            WHERE s.user_id = :userId
              AND p.category = (SELECT category FROM problems WHERE id = :problemId)
            GROUP BY p.category
            HAVING count(*) >= :threshold
            """,
        )
            .param("userId", userId)
            .param("problemId", problemId)
            .param("threshold", Badge.CATEGORY_THRESHOLD)
            .query { rs, _ -> Badge.categoryCode(ProblemCategory.valueOf(rs.getString("category"))) }
            .optional()
            .orElse(null)
}
