package codekr.api.contest.service

import codekr.api.common.dto.PageResponse
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.contest.dto.ContestDetailResponse
import codekr.api.contest.dto.ContestProblemResponse
import codekr.api.contest.dto.ContestSummaryResponse
import codekr.api.contest.entity.Contest
import codekr.api.contest.entity.ContestRegistration
import codekr.api.contest.entity.ContestRegistrationId
import codekr.api.contest.entity.ContestStatus
import codekr.api.contest.repository.ContestProblemRepository
import codekr.api.contest.repository.ContestRegistrationRepository
import codekr.api.contest.repository.ContestRepository
import codekr.api.problem.repository.ProblemRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 대회 조회와 참가 (#61).
 *
 * **진행 단계는 저장하지 않고 조회 시점에 판정한다.** 스케줄러가 상태를 옮기는 방식이면
 * 스케줄러가 1분 늦는 순간 대회가 1분 늦게 시작한다 — 지연이 곧 사고가 된다.
 */
@Service
@Transactional(readOnly = true)
class ContestService(
    private val contestRepository: ContestRepository,
    private val contestProblemRepository: ContestProblemRepository,
    private val registrationRepository: ContestRegistrationRepository,
    private val problemRepository: ProblemRepository,
) {

    fun findAll(pageable: Pageable): PageResponse<ContestSummaryResponse> {
        val now = Instant.now()
        // 준비 중인 대회는 어드민만 본다.
        val page = contestRepository
            .findByStatusNotAndDeletedAtIsNullOrderByStartsAtDesc(ContestStatus.DRAFT, pageable)
        return PageResponse.from(page.map { summaryOf(it, now) })
    }

    fun findDetail(slug: String, viewerId: Long?): ContestDetailResponse {
        val now = Instant.now()
        val contest = requirePublic(slug)
        val registered = viewerId != null && isRegistered(contest.id, viewerId)

        return ContestDetailResponse(
            summary = summaryOf(contest, now),
            description = contest.description,
            freezeAt = contest.freezeAt,
            registered = registered,
            canRegister = viewerId != null && !registered && contest.canRegisterAt(now),
            problems = problemsFor(contest, registered, now),
        )
    }

    @Transactional
    fun register(slug: String, userId: Long) {
        val contest = requirePublic(slug)
        if (!contest.canRegisterAt(Instant.now())) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "지금은 참가 등록을 받지 않습니다.")
        }
        // 이미 등록했으면 아무 일도 없다 — 두 번 눌러도 오류가 아니어야 한다.
        val id = ContestRegistrationId(contest.id, userId)
        if (!registrationRepository.existsById(id)) {
            registrationRepository.save(ContestRegistration(id))
        }
    }

    /**
     * 문제 목록.
     *
     * **시작 전에는 참가자에게도 보이지 않는다.** 등록만 하면 문제를 미리 볼 수 있다면
     * 대회가 성립하지 않는다.
     */
    private fun problemsFor(
        contest: Contest,
        registered: Boolean,
        now: Instant,
    ): List<ContestProblemResponse> {
        val phase = contest.phaseAt(now)
        if (!phase.problemsVisible) return emptyList()
        // 진행 중에는 참가자만 본다. 종료 후에는 누구나 본다 (기획서 §2).
        if (phase == codekr.api.contest.entity.ContestPhase.RUNNING && !registered) return emptyList()

        val assignments = contestProblemRepository.findByIdContestIdOrderBySeqAsc(contest.id)
        val problems = problemRepository.findAllById(assignments.map { it.problemId }).associateBy { it.id }

        return assignments.mapNotNull { assignment ->
            val problem = problems[assignment.problemId] ?: return@mapNotNull null
            ContestProblemResponse(
                label = labelOf(assignment.seq),
                slug = problem.slug,
                title = problem.title,
                score = assignment.score,
                excluded = assignment.isExcluded,
            )
        }
    }

    private fun summaryOf(contest: Contest, now: Instant) =
        ContestSummaryResponse.of(contest, now, registrationRepository.countByIdContestId(contest.id))

    private fun isRegistered(contestId: Long, userId: Long) =
        registrationRepository.existsById(ContestRegistrationId(contestId, userId))

    private fun requirePublic(slug: String): Contest {
        val contest = contestRepository.findBySlugAndDeletedAtIsNull(slug)
            ?: throw ApiException(ErrorCode.CONTEST_NOT_FOUND)
        // 준비 중인 대회는 없는 것과 같다 — 존재 여부도 알리지 않는다.
        if (contest.status == ContestStatus.DRAFT) throw ApiException(ErrorCode.CONTEST_NOT_FOUND)
        return contest
    }

    companion object {
        /** 1 → A, 2 → B … 26 을 넘으면 AA, AB 로 이어진다. */
        fun labelOf(seq: Int): String {
            var remaining = seq
            val builder = StringBuilder()
            while (remaining > 0) {
                val index = (remaining - 1) % 26
                builder.append('A' + index)
                remaining = (remaining - 1) / 26
            }
            return builder.reverse().toString()
        }
    }
}
