package codekr.api.contest.scoreboard

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.contest.entity.Contest
import codekr.api.contest.entity.ContestStatus
import codekr.api.contest.repository.ContestProblemRepository
import codekr.api.contest.repository.ContestRepository
import codekr.api.contest.service.ContestService
import codekr.api.problem.repository.ProblemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * 대회 순위표 (#63).
 *
 * **동결은 "결과를 감추는" 장치다.** 시도했다는 사실은 감추지 않는다 — 감추면 순위표가
 * 대회 후반에 아무 정보도 주지 않고, 관전의 재미도 사라진다 (#86).
 */
@Service
@Transactional(readOnly = true)
class ScoreboardService(
    private val contestRepository: ContestRepository,
    private val contestProblemRepository: ContestProblemRepository,
    private val problemRepository: ProblemRepository,
    private val scoreboardRepository: ScoreboardRepository,
    private val cache: ScoreboardCache,
) {

    /**
     * @param asAdmin 어드민은 동결 중에도 실제 순위를 본다.
     */
    fun of(slug: String, asAdmin: Boolean): ScoreboardResponse {
        val now = Instant.now()
        val contest = contestRepository.findBySlugAndDeletedAtIsNull(slug)
            ?: throw ApiException(ErrorCode.CONTEST_NOT_FOUND)
        if (contest.status == ContestStatus.DRAFT && !asAdmin) {
            throw ApiException(ErrorCode.CONTEST_NOT_FOUND)
        }

        val frozen = contest.frozenAt(now)
        // 어드민 화면은 캐시하지 않는다. 운영 판단에 쓰는 숫자가 몇 초 늦으면 안 된다.
        if (asAdmin) return build(contest, cutoff = null, frozen = frozen)

        return cache.get(contest.id) {
            build(contest, cutoff = if (frozen) contest.freezeAt else null, frozen = frozen)
        }
    }

    private fun build(contest: Contest, cutoff: Instant?, frozen: Boolean): ScoreboardResponse {
        val assignments = contestProblemRepository.findByIdContestIdOrderBySeqAsc(contest.id)
        val problems = problemRepository.findAllById(assignments.map { it.problemId }).associateBy { it.id }
        val cells = scoreboardRepository.cells(contest.id, cutoff)
            .groupBy { it.userId }
            .mapValues { (_, list) -> list.associateBy { it.problemId } }

        val rows = scoreboardRepository.participants(contest.id)
            .map { participant -> rowOf(participant, assignments, cells[participant.userId].orEmpty(), contest) }
            .sortedWith(
                // 총점 → 마지막 득점 시각이 이른 순 → 등록 시각 → 사용자 ID.
                // 마지막 키까지 결정적이어야 페이지 사이에서 순서가 흔들리지 않는다.
                compareByDescending<Ranked> { it.totalScore }
                    .thenBy { it.lastSolvedAt ?: Instant.MAX }
                    .thenBy { it.registeredAt }
                    .thenBy { it.userId },
            )

        val solvedCounts = assignments.associate { assignment ->
            assignment.problemId to cells.values.count { it[assignment.problemId]?.solvedAt != null }
        }

        return ScoreboardResponse(
            contestSlug = contest.slug,
            frozen = frozen,
            frozenAt = if (frozen) contest.freezeAt else null,
            rejudgeInProgress = scoreboardRepository.rejudgeInProgress(contest.id),
            problems = assignments.mapNotNull { assignment ->
                val problem = problems[assignment.problemId] ?: return@mapNotNull null
                ScoreboardProblem(
                    label = ContestService.labelOf(assignment.seq),
                    slug = problem.slug,
                    title = problem.title,
                    score = assignment.score,
                    excluded = assignment.isExcluded,
                    solvedCount = solvedCounts[assignment.problemId] ?: 0,
                )
            },
            rows = rows.mapIndexed { index, ranked -> ranked.toResponse(index + 1) },
        )
    }

    private fun rowOf(
        participant: ScoreboardParticipant,
        assignments: List<codekr.api.contest.entity.ContestProblem>,
        cells: Map<Long, ScoreboardCell>,
        contest: Contest,
    ): Ranked {
        val responses = assignments.map { assignment ->
            val cell = cells[assignment.problemId]
            ScoreboardCellResponse(
                solved = cell?.solvedAt != null,
                solvedMinutes = cell?.solvedAt?.let {
                    Duration.between(contest.startsAt, it).toMinutes().toInt()
                },
                attempts = cell?.attempts ?: 0,
                pending = cell?.pending ?: 0,
            )
        }
        // **제외된 문제는 점수를 주지 않는다.** 시도 기록은 남는다 (#86).
        val scored = assignments.filter { !it.isExcluded && cells[it.problemId]?.solvedAt != null }

        return Ranked(
            userId = participant.userId,
            nickname = participant.nickname,
            handle = participant.handle,
            registeredAt = participant.registeredAt,
            totalScore = scored.sumOf { it.score },
            solvedCount = scored.size,
            lastSolvedAt = scored.mapNotNull { cells[it.problemId]?.solvedAt }.maxOrNull(),
            cells = responses,
        )
    }

    private data class Ranked(
        val userId: Long,
        val nickname: String,
        val registeredAt: Instant,
        val handle: String,
        val totalScore: Int,
        val solvedCount: Int,
        val lastSolvedAt: Instant?,
        val cells: List<ScoreboardCellResponse>,
    ) {
        fun toResponse(rank: Int) =
            ScoreboardRow(rank, nickname, handle, totalScore, solvedCount, lastSolvedAt, cells)
    }
}
