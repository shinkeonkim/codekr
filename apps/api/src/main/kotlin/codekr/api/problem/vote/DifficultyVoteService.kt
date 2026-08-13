package codekr.api.problem.vote

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.DifficultyState
import codekr.api.problem.repository.ProblemRepository
import codekr.api.ranking.service.ProblemScoreResyncService
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 난이도 투표 (#477).
 *
 * **사용자가 사이트 자체를 낫게 만드는 첫 통로다.** 지금까지 사용자가 만드는 것은
 * 제출·글·댓글뿐이었고 전부 자기 것이었다.
 *
 * ## 푼 사람만 투표한다
 *
 * 안 풀고 난이도를 매기는 것은 뜻이 약하다. "어려워서 못 풀었다" 도 정보이긴 하지만,
 * **그것과 "풀어 보니 이 정도였다" 를 한 숫자에 섞으면** 그 숫자가 무엇을 재는지 알 수
 * 없게 된다. 못 푼 사람의 체감은 정답률(#84)이 이미 말한다.
 *
 * ## 중앙값이다
 *
 * 티어는 **순서가 있는 값**이고, 표가 적을 때 극단값 하나가 평균을 끌고 간다.
 * 장난 표 한 장이 난이도를 바꾸면 그 순간 이 기능은 신뢰를 잃는다.
 *
 * ## `UNRATED` 만 자동으로 채운다
 *
 * 어드민이 정한 난이도를 투표가 덮으면, 표가 적을 때 난이도가 흔들린다. 반대로 제안만
 * 하면 어드민의 일이 줄지 않는다. **비어 있는 자리(#195 의 `UNRATED`)를 채우는 것**은
 * 그 둘 사이에서 값이 가장 크다 — 지금 아무 숫자도 없는 문제에 숫자가 생기기 때문이다.
 *
 * 난이도가 바뀌면 **점수를 함께 다시 계산한다** (#194). 그러지 않으면 이미 맞힌 사람의
 * 점수가 옛 난이도로 남고, 투표로 난이도가 자주 움직일수록 그 어긋남이 커진다.
 */
@Service
class DifficultyVoteService(
    private val voteRepository: DifficultyVoteRepository,
    private val problemRepository: ProblemRepository,
    private val scoreResyncService: ProblemScoreResyncService,
    private val jdbcClient: JdbcClient,
    private val properties: DifficultyVoteProperties,
) {

    @Transactional
    fun vote(problemId: Long, userId: Long, level: Int): DifficultyVoteResponse {
        if (Difficulty.ofLevelOrNull(level) == null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "난이도는 1~30 사이여야 합니다.")
        }
        if (!hasSolved(problemId, userId)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "문제를 푼 사람만 난이도를 매길 수 있습니다.")
        }

        // **바꿀 수 있다.** 문제가 고쳐졌을 때(테스트케이스가 늘거나 지문이 명확해졌을 때)
        // 옛 판단이 그대로 남으면, 모인 숫자가 지금의 문제를 말하지 않게 된다.
        val existing = voteRepository.findByProblemIdAndUserId(problemId, userId)
        if (existing == null) {
            voteRepository.save(DifficultyVote(problemId, userId, level))
        } else {
            existing.level = level
        }
        voteRepository.flush()

        applyToUnrated(problemId)
        return summary(problemId, userId)
    }

    /** 내 표와, **내가 투표한 뒤에만** 분포를 준다. */
    @Transactional(readOnly = true)
    fun summary(problemId: Long, userId: Long?): DifficultyVoteResponse {
        val mine = userId?.let { voteRepository.findByProblemIdAndUserId(problemId, it) }
        val levels = voteRepository.levelsOf(problemId)
        return DifficultyVoteResponse(
            myLevel = mine?.level,
            voteCount = levels.size,
            // **투표하기 전에는 분포를 감춘다.** 먼저 보면 뒤에 오는 사람이 끌려간다 —
            // 그러면 모인 숫자는 문제의 난이도가 아니라 처음 몇 표의 메아리가 된다.
            medianLevel = if (mine == null) null else median(levels),
            canVote = userId != null && hasSolved(problemId, userId),
        )
    }

    /**
     * 표가 충분히 모이면 `UNRATED` 문제에 난이도를 붙인다.
     *
     * 문턱을 **설정으로 뺀 이유**: 지금 이 사이트는 사용자도 문제도 적다. 값을 코드에
     * 박고 높게 잡으면 **기능이 켜져 있는데 아무도 못 쓰는 상태**가 되고, 낮게 박으면
     * 나중에 올릴 때 배포가 필요하다.
     */
    private fun applyToUnrated(problemId: Long) {
        val problem = problemRepository.findById(problemId).orElse(null) ?: return
        if (problem.difficultyState != DifficultyState.UNRATED) return

        val levels = voteRepository.levelsOf(problemId)
        if (levels.size < properties.minVotes) return

        problem.difficulty = Difficulty.ofLevelOrNull(median(levels)) ?: return
        problemRepository.flush()
        // 난이도가 생겼으므로 이미 맞힌 사람들의 점수가 따라와야 한다 (#194).
        scoreResyncService.resync(problemId)
    }

    /** 이 문제를 풀었는가. 랭킹 점수 표가 곧 "푼 기록" 이다 (#57). */
    private fun hasSolved(problemId: Long, userId: Long): Boolean =
        jdbcClient.sql("SELECT count(*) FROM user_problem_scores WHERE problem_id = :p AND user_id = :u")
            .param("p", problemId)
            .param("u", userId)
            .query(Int::class.java)
            .single() > 0

    private fun median(levels: List<Int>): Int? {
        if (levels.isEmpty()) return null
        val sorted = levels.sorted()
        val middle = sorted.size / 2
        // 짝수면 **아래쪽**을 고른다. 티어는 정수 눈금이고, 반올림으로 한 칸 올리면
        // 아무도 그렇게 투표하지 않은 값이 난이도가 된다.
        return if (sorted.size % 2 == 1) sorted[middle] else sorted[middle - 1]
    }
}

/** 내 표와 모인 결과 (#477). */
data class DifficultyVoteResponse(
    val myLevel: Int?,
    val voteCount: Int,
    /** 내가 투표한 뒤에만 채워진다 — 먼저 보면 끌려간다. */
    val medianLevel: Int?,
    val canVote: Boolean,
)
