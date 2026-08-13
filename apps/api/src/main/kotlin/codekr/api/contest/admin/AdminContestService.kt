package codekr.api.contest.admin

import codekr.api.common.dto.PageResponse
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.contest.entity.Contest
import codekr.api.contest.entity.ContestProblem
import codekr.api.contest.entity.ContestProblemId
import codekr.api.contest.entity.ContestPhase
import codekr.api.contest.entity.ContestStatus
import codekr.api.contest.repository.ContestProblemRepository
import codekr.api.contest.repository.ContestRegistrationRepository
import codekr.api.contest.repository.ContestRepository
import codekr.api.contest.service.ContestService
import codekr.api.problem.repository.ProblemRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 대회 운영 (#61). 생성·수정·상태 전이·문제 배정. */
@Service
@Transactional(readOnly = true)
class AdminContestService(
    private val clock: java.time.Clock,
    private val contestRepository: ContestRepository,
    private val contestProblemRepository: ContestProblemRepository,
    private val registrationRepository: ContestRegistrationRepository,
    private val problemRepository: ProblemRepository,
) {

    fun findAll(pageable: Pageable): PageResponse<AdminContestResponse> {
        val now = Instant.now()
        return PageResponse.from(
            contestRepository.findByDeletedAtIsNullOrderByStartsAtDesc(pageable)
                .map { responseOf(it, now, withProblems = false) },
        )
    }

    fun findDetail(id: Long): AdminContestResponse = responseOf(require(id), Instant.now())

    @Transactional
    fun create(request: ContestUpsertRequest, createdBy: Long): AdminContestResponse {
        if (contestRepository.existsBySlugAndDeletedAtIsNull(request.slug)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이미 쓰고 있는 slug 입니다.")
        }
        validate(request)

        val contest = contestRepository.save(
            Contest(
                slug = request.slug,
                title = request.title,
                description = request.description,
                startsAt = request.startsAt,
                endsAt = request.endsAt,
                freezeMinutes = request.freezeMinutes,
                submissionCooldownSeconds = request.submissionCooldownSeconds,
                registrationOpenDuring = request.registrationOpenDuring,
                createdBy = createdBy,
            ),
        )
        contest.visibility = request.visibility
        replaceProblems(contest.id, request)
        return responseOf(contest, Instant.now())
    }

    @Transactional
    fun update(id: Long, request: ContestUpsertRequest): AdminContestResponse {
        val contest = require(id)
        /*
            **진행 중인 대회는 고칠 수 없다** (#335).

            대회 중에 시작 시각이나 문제 목록이 바뀌면 **이미 제출한 사람과 아닌 사람이
            다른 대회를 본 것**이 된다. 경고로 두면 언젠가 눌린다.

            **서버가 막는다** — 화면만 막으면 API 는 그대로 열려 있다. 그리고 막히는 것은
            **수정**이지 운영이 아니다: 공지·질의 답변(#147)과 순위 공개(#86)는
            다른 경로라 그대로 된다. 함께 막으면 대회를 운영할 수 없다.
        */
        if (contest.phaseAt(clock.instant()) == ContestPhase.RUNNING) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "진행 중인 대회는 고칠 수 없습니다. 공지와 질의 답변은 그대로 됩니다.",
            )
        }
        if (contest.slug != request.slug && contestRepository.existsBySlugAndDeletedAtIsNull(request.slug)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이미 쓰고 있는 slug 입니다.")
        }
        validate(request)

        contest.apply {
            slug = request.slug
            title = request.title
            description = request.description
            startsAt = request.startsAt
            endsAt = request.endsAt
            freezeMinutes = request.freezeMinutes
            submissionCooldownSeconds = request.submissionCooldownSeconds
            registrationOpenDuring = request.registrationOpenDuring
            visibility = request.visibility
        }
        replaceProblems(contest.id, request)
        return responseOf(contest, Instant.now())
    }

    @Transactional
    fun changeStatus(id: Long, next: ContestStatus): AdminContestResponse {
        val contest = require(id)
        // 공개하려면 풀 문제가 있어야 한다. 빈 대회를 공개하면 참가자가 등록한 뒤
        // 시작 시각에 아무것도 못 본다.
        if (next == ContestStatus.PUBLISHED && contestProblemRepository
                .findByIdContestIdOrderBySeqAsc(id).none { !it.isExcluded }
        ) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "배정된 문제가 없으면 공개할 수 없습니다.")
        }
        when (next) {
            ContestStatus.PUBLISHED -> contest.publish()
            ContestStatus.CANCELED -> contest.cancel()
            ContestStatus.ARCHIVED -> contest.archive()
            ContestStatus.DRAFT -> throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "공개한 대회를 준비 중으로 되돌릴 수 없습니다.",
            )
        }
        return responseOf(contest, Instant.now())
    }

    /** 최종 순위를 공개한다 (#86). 종료 후에만 할 수 있다. */
    @Transactional
    fun unfreeze(id: Long): AdminContestResponse {
        val contest = require(id)
        val now = Instant.now()
        if (now < contest.endsAt) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "대회가 끝난 뒤에 공개할 수 있습니다.")
        }
        contest.unfreeze(now)
        return responseOf(contest, now)
    }

    /**
     * 문제를 대회에서 제외한다 (#86).
     *
     * 배정을 지우지 않는 이유는 그 문제로 낸 제출이 남아 있기 때문이다.
     * 제외는 프리즈로 감추지 않고 **즉시 반영한다.**
     */
    @Transactional
    fun excludeProblem(id: Long, problemId: Long, excluded: Boolean): AdminContestResponse {
        require(id)
        val assignment = contestProblemRepository.findById(ContestProblemId(id, problemId))
            .orElseThrow { ApiException(ErrorCode.PROBLEM_NOT_FOUND) }
        if (excluded) assignment.exclude() else assignment.restore()
        return responseOf(require(id), Instant.now())
    }

    @Transactional
    fun delete(id: Long) {
        val contest = require(id)
        // 시작한 대회는 지우지 않는다 — 제출 이력이 딸려 있다.
        if (contest.status != ContestStatus.DRAFT) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "공개한 대회는 삭제할 수 없습니다. 취소를 쓰십시오.")
        }
        contest.delete()
    }

    private fun validate(request: ContestUpsertRequest) {
        if (request.endsAt <= request.startsAt) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "종료 시각은 시작 시각보다 뒤여야 합니다.")
        }
        // 동결이 시작보다 앞서면 대회 내내 동결된 것과 같다.
        if (request.freezeMinutes > 0) {
            val freezeAt = request.endsAt.minusSeconds(request.freezeMinutes * 60L)
            if (freezeAt <= request.startsAt) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "동결 시간이 대회 길이보다 깁니다.")
            }
        }
        if (request.problems.groupingBy { it.seq }.eachCount().any { it.value > 1 }) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "문제 순번이 중복되었습니다.")
        }
        if (request.problems.groupingBy { it.problemId }.eachCount().any { it.value > 1 }) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "같은 문제가 두 번 배정되었습니다.")
        }
        request.problems.forEach {
            problemRepository.findByIdAndDeletedAtIsNull(it.problemId)
                ?: throw ApiException(ErrorCode.PROBLEM_NOT_FOUND, "없는 문제를 배정했습니다: ${it.problemId}")
        }
    }

    /** 배정은 전체 치환한다. 부분 수정은 순번이 꼬인다. */
    private fun replaceProblems(contestId: Long, request: ContestUpsertRequest) {
        contestProblemRepository.deleteByIdContestId(contestId)
        contestProblemRepository.flush()
        contestProblemRepository.saveAll(
            request.problems.map { ContestProblem(ContestProblemId(contestId, it.problemId), it.seq, it.score) },
        )
    }

    private fun responseOf(contest: Contest, now: Instant, withProblems: Boolean = true): AdminContestResponse {
        val problems = if (withProblems) problemsOf(contest.id) else emptyList()
        return AdminContestResponse.of(
            contest,
            now,
            registrationRepository.countByIdContestId(contest.id),
            problems,
        )
    }

    private fun problemsOf(contestId: Long): List<AdminContestProblemResponse> {
        val assignments = contestProblemRepository.findByIdContestIdOrderBySeqAsc(contestId)
        val problems = problemRepository.findAllById(assignments.map { it.problemId }).associateBy { it.id }

        return assignments.mapNotNull { assignment ->
            val problem = problems[assignment.problemId] ?: return@mapNotNull null
            AdminContestProblemResponse(
                problemId = problem.id,
                label = ContestService.labelOf(assignment.seq),
                slug = problem.slug,
                title = problem.title,
                seq = assignment.seq,
                score = assignment.score,
                excluded = assignment.isExcluded,
            )
        }
    }

    private fun require(id: Long): Contest =
        contestRepository.findByIdAndDeletedAtIsNull(id) ?: throw ApiException(ErrorCode.CONTEST_NOT_FOUND)
}
