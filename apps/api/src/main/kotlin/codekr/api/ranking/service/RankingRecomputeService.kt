package codekr.api.ranking.service

import codekr.api.ranking.badge.BadgeAwarder
import codekr.api.ranking.repository.UserProblemScoreRepository
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * 랭킹 집계를 원자료에서 다시 만든다 (#177).
 *
 * **집계를 저장하기로 한 이상 이 경로가 반드시 있어야 한다** — 활동 집계(#105)가 먼저
 * 지킨 규칙이다. 값이 어긋났을 때 되돌릴 방법이 없으면 애초에 저장하면 안 되는 것이다.
 *
 * 특히 기능을 처음 붙였을 때가 그렇다. 그 전에 맞힌 제출은 `ScoreRecorder` 를 거치지
 * 않았으므로, 이 경로가 없으면 **랭킹이 빈 채로 시작한다.**
 */
@Service
class RankingRecomputeService(
    private val scoreRepository: UserProblemScoreRepository,
    private val userRepository: UserRepository,
    private val badgeAwarder: BadgeAwarder,
    private val transactionTemplate: TransactionTemplate,
) {

    /**
     * 한 사용자의 점수·최고 점수·뱃지를 다시 맞춘다.
     *
     * @return 다시 계산한 뒤의 점수와 맞힌 문제 수.
     */
    @Transactional
    fun recompute(userId: Long): RecomputeResult {
        scoreRepository.recomputeAll(userId)
        val (score, solvedCount) = scoreRepository.totalsOf(userId)

        // 실력 티어는 최고 점수로 정한다 (#58) — 다시 계산해도 **내려가지 않는다.**
        // 여기서 맞추지 않으면 점수만 되살아나고 티어는 계속 비어 있다.
        userRepository.findById(userId).ifPresent { user ->
            if (score > user.peakScore) user.peakScore = score
        }

        // 뱃지 조건은 매번 원자료에서 확인하므로 문제마다 다시 불러도 안전하다.
        // 이미 받은 것은 다시 주지 않는다.
        scoreRepository.solvedProblemIds(userId).forEach { badgeAwarder.onAccepted(userId, it) }

        return RecomputeResult(score = score, solvedCount = solvedCount)
    }

    /**
     * 맞힌 제출이 있는 모든 사용자를 다시 계산한다.
     *
     * **기능 도입 직후에 필요한 것은 이쪽이다.** 사용자를 하나씩 부르는 것으로는 이미
     * 쌓인 제출을 되살릴 수 없다.
     *
     * @return 다시 계산한 사용자 수.
     */
    fun recomputeEveryone(): Int {
        val userIds = scoreRepository.userIdsWithAcceptedSubmissions()
        // 사용자마다 트랜잭션을 끊는다. 한 명이 실패해도 나머지는 남는다 — 전부 되돌리면
        // 다시 처음부터 돌려야 하고, 사용자가 많을수록 그 대가가 커진다.
        //
        // **같은 빈 안에서 부르면 @Transactional 이 걸리지 않으므로** 여기서 직접 연다.
        userIds.forEach { userId -> transactionTemplate.executeWithoutResult { recompute(userId) } }
        return userIds.size
    }
}

data class RecomputeResult(val score: Int, val solvedCount: Int)
