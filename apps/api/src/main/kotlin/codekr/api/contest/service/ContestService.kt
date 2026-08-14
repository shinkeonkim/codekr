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
import codekr.api.contest.entity.ContestRegistrationStatus
import codekr.api.contest.entity.ContestStatus
import codekr.api.contest.entity.ContestVisibility
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
        // 준비 중인 대회는 어드민만 본다. **목록에 없는 대회(#465)도 여기 오지 않는다.**
        val page = contestRepository.findByStatusNotAndVisibilityAndDeletedAtIsNullOrderByStartsAtDesc(
            ContestStatus.DRAFT,
            ContestVisibility.PUBLIC,
            pageable,
        )
        return PageResponse.from(page.map { summaryOf(it, now) })
    }

    /**
     * 내가 등록한 대회 (#465).
     *
     * **목록에 없는 대회에 들어간 사람이 그것을 다시 찾는 길이다.** 이 목록에는 범위와
     * 무관하게 나온다 — 이미 들어간 대회이므로 감출 이유가 없다.
     */
    fun findRegistered(userId: Long, pageable: Pageable): PageResponse<ContestSummaryResponse> {
        val now = Instant.now()
        val page = contestRepository.findRegistered(userId, ContestStatus.DRAFT, pageable)
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
            // 신청은 했는데 아직 승인 전인가 (#466). 화면이 "심사 중" 을 말한다.
            pendingApproval = viewerId != null && !registered && hasApplied(contest.id, viewerId),
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
            /*
                승인이 필요한 대회면 **신청까지만** 된다 (#466).

                기본은 승인이다 — 지금까지의 대회가 전부 그렇고, 기본을 대기로 두면
                그 대회들이 전부 막힌다.
            */
            val registration = ContestRegistration(id).apply {
                if (contest.requiresApproval) status = ContestRegistrationStatus.PENDING
            }
            registrationRepository.save(registration)
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
                id = problem.id,
                slug = problem.slug,
                title = problem.title,
                score = assignment.score,
                excluded = assignment.isExcluded,
            )
        }
    }

    private fun summaryOf(contest: Contest, now: Instant) =
        ContestSummaryResponse.of(contest, now, registrationRepository.countByIdContestId(contest.id))

    /**
     * 참가자인가 (#466).
     *
     * **"등록했다" 가 아니라 "승인됐다" 이다.** 승인이 필요한 대회에서는 신청만 한
     * 사람이 생기고, 그 사람은 아직 참가자가 아니다 — 문제도 못 보고 제출도 못 한다.
     *
     * 이 판정을 쓰는 곳이 넷이다(상세·제출·질의·순위표). **한 곳에 두지 않으면
     * 한 군데만 고쳐도 "화면에는 참가자인데 제출은 막히는" 상태가 생긴다.**
     */
    fun isParticipant(contestId: Long, userId: Long): Boolean =
        registrationRepository.findById(ContestRegistrationId(contestId, userId))
            .map { it.approved }
            .orElse(false)

    /** 신청은 했는가 (승인 여부와 무관). 화면이 "심사 중" 을 말하는 데 쓴다. */
    fun hasApplied(contestId: Long, userId: Long): Boolean =
        registrationRepository.existsById(ContestRegistrationId(contestId, userId))

    private fun isRegistered(contestId: Long, userId: Long) = isParticipant(contestId, userId)

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
