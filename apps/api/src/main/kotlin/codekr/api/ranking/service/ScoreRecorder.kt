package codekr.api.ranking.service

import codekr.api.ranking.repository.UserProblemScoreRepository
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 채점이 끝난 제출을 랭킹 점수에 반영한다 (#57).
 *
 * **한 문제는 최초 정답 1회만 점수를 준다.** 같은 문제를 다시 맞혀도 늘지 않는다 —
 * 표의 기본키가 (사용자, 문제)라서 구조적으로 그렇게 된다.
 */
@Component
class ScoreRecorder(
    private val scoreRepository: UserProblemScoreRepository,
    private val userRepository: UserRepository,
) {

    /**
     * @return 점수 변화량. 재채점으로 정답이 뒤집히면 **음수**가 된다.
     */
    @Transactional
    fun record(userId: Long, problemId: Long): Int {
        val delta = scoreRepository.refresh(userId, problemId)

        // 도달했던 최고 점수를 남긴다. 실력 티어는 이 값으로 정한다 — **강등이 없기 때문이다** (#58).
        if (delta > 0) {
            val user = userRepository.findById(userId).orElse(null) ?: return delta
            val score = scoreRepository.totalsOf(userId).first
            if (score > user.peakScore) user.peakScore = score
        }
        return delta
    }
}
