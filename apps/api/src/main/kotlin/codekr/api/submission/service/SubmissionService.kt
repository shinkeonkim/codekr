package codekr.api.submission.service

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.dto.PageResponse
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.properties.SubmissionProperties
import codekr.api.problem.entity.Problem
import codekr.api.problem.repository.ProblemRepository
import codekr.api.problem.service.ProblemService
import codekr.api.queue.QueuePublisher
import codekr.api.queue.JudgePriority
import codekr.api.queue.message.JudgeJobMessage
import codekr.api.runtime.RuntimeRegistry
import codekr.api.user.repository.UserRepository
import codekr.api.submission.dto.RunRequest
import codekr.api.submission.dto.RunResponse
import codekr.api.submission.dto.SubmissionDetailResponse
import codekr.api.submission.dto.SubmissionSummaryResponse
import codekr.api.submission.dto.SubmitRequest
import codekr.api.submission.dto.SubmitResponse
import codekr.api.submission.dto.VisibilityChangeRequest
import codekr.api.submission.entity.Submission
import codekr.api.submission.entity.SubmissionKind
import codekr.api.submission.entity.SubmissionVisibility
import codekr.api.submission.repository.SubmissionRepository
import codekr.api.submission.repository.SubmissionSearchCondition
import codekr.api.submission.repository.SubmissionSearchRepository
import codekr.api.submission.repository.SubmissionTestcaseResultRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
@Transactional(readOnly = true)
class SubmissionService(
    private val submissionRepository: SubmissionRepository,
    private val resultRepository: SubmissionTestcaseResultRepository,
    private val searchRepository: SubmissionSearchRepository,
    private val problemRepository: ProblemRepository,
    private val problemService: ProblemService,
    private val runtimeRegistry: RuntimeRegistry,
    private val queuePublisher: QueuePublisher,
    private val userRepository: UserRepository,
    private val properties: SubmissionProperties,
) {

    /** 임의 입력으로 1회 실행한다. 채점하지 않으므로 제출 이력을 남기지 않는다. */
    fun run(slug: String, request: RunRequest): RunResponse {
        val problem = problemService.requirePublished(slug)
        validate(problem, request.runtimeId, request.sourceCode)
        // 실행도 제출과 같은 제한을 써야 한다. 실행에서 통과한 코드가 제출에서 TLE 나면
        // 사용자는 왜 그런지 알 수 없다.
        val limits = problem.limitsFor(request.runtimeId)

        return RunResponse.from(
            queuePublisher.runOnce(
                runtimeId = request.runtimeId,
                sourceCode = request.sourceCode,
                stdin = request.stdin,
                timeLimitMs = limits.timeLimitMs,
                memoryLimitMb = limits.memoryLimitMb,
                waitTimeout = Duration.ofSeconds(RUN_WAIT_SECONDS),
            ),
        )
    }

    @Transactional
    fun submit(slug: String, userId: Long, request: SubmitRequest): SubmitResponse {
        val problem = problemService.requirePublished(slug)
        validate(problem, request.runtimeId, request.sourceCode)
        if (problem.testcases.isEmpty()) throw ApiException(ErrorCode.TESTCASE_REQUIRED)

        val submission = submissionRepository.save(
            Submission(
                userId = userId,
                problemId = problem.id,
                runtimeId = request.runtimeId,
                sourceCode = request.sourceCode,
                totalCount = problem.testcases.size,
                // 요청에 없으면 사용자 기본값을 쓴다 (#104).
                // **서버에서 채운다** — 화면이 기본값을 알고 보내는 방식이면 화면마다 어긋난다.
            ).apply { changeVisibility(request.visibility ?: defaultVisibilityOf(userId)) },
        )

        queuePublisher.publishJudgeJob(
            JudgeJobMessage.of(submission, problem),
            JudgePriority.of(submission.kind, problem),
        )
        return SubmitResponse.from(submission)
    }

    fun findDetail(id: Long, principal: AuthPrincipal): SubmissionDetailResponse {
        val submission = submissionRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw ApiException(ErrorCode.SUBMISSION_NOT_FOUND)
        // 검증 제출의 소스는 정답 코드다 — 어드민만 볼 수 있어야 한다.
        if (submission.kind != SubmissionKind.USER && !principal.isAdmin) {
            throw ApiException(ErrorCode.SUBMISSION_NOT_FOUND)
        }

        return SubmissionDetailResponse.of(
            submission = submission,
            // 삭제된 문제라도 이력에는 보여야 하므로 소프트 삭제 여부를 따지지 않고 조회한다.
            problem = problemRepository.findById(submission.problemId).orElse(null),
            results = resultRepository.findBySubmissionIdOrderBySeqAsc(id),
            nickname = nicknameOf(submission.userId),
            sourceVisible = submission.isSourceVisibleTo(principal.userId, principal.isAdmin),
        )
    }

    /** 공개 범위는 작성자만 바꿀 수 있다. 시점 제한은 두지 않는다 (docs/03 참고). */
    @Transactional
    fun changeVisibility(id: Long, principal: AuthPrincipal, request: VisibilityChangeRequest) {
        val submission = submissionRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw ApiException(ErrorCode.SUBMISSION_NOT_FOUND)
        if (submission.userId != principal.userId) throw ApiException(ErrorCode.FORBIDDEN)

        submission.changeVisibility(request.visibility)
    }

    /**
     * 전체 회원의 제출 목록 (#34).
     * 소스 코드는 담지 않고, 상세로 들어갔을 때 볼 수 있는지만 `sourceVisible` 로 알린다.
     */
    fun search(
        condition: SubmissionSearchCondition,
        pageable: Pageable,
        principal: AuthPrincipal,
    ): PageResponse<SubmissionSummaryResponse> {
        val page = searchRepository.search(condition, pageable)

        // 목록에 필요한 문제·회원 정보를 한 번에 모아 N+1 을 피한다.
        val problems = problemRepository.findAllById(page.content.map { it.problemId }).associateBy { it.id }
        val nicknames = userRepository.findAllById(page.content.map { it.userId })
            .associate { it.id to it.nickname }

        return PageResponse.from(
            page.map { submission ->
                SubmissionSummaryResponse.of(
                    submission = submission,
                    problem = problems[submission.problemId],
                    nickname = nicknames[submission.userId] ?: "(탈퇴한 사용자)",
                    sourceVisible = submission.isSourceVisibleTo(principal.userId, principal.isAdmin),
                )
            },
        )
    }

    private fun defaultVisibilityOf(userId: Long): SubmissionVisibility =
        userRepository.findById(userId)
            .map { it.defaultSubmissionVisibility }
            .orElse(SubmissionVisibility.PRIVATE)

    private fun nicknameOf(userId: Long): String =
        userRepository.findById(userId).map { it.nickname }.orElse("(탈퇴한 사용자)")

    fun findMine(userId: Long, problemSlug: String?, pageable: Pageable): PageResponse<SubmissionSummaryResponse> {
        val page = problemSlug
            ?.let {
                problemRepository.findBySlugAndDeletedAtIsNull(it)
                    ?: throw ApiException(ErrorCode.PROBLEM_NOT_FOUND)
            }
            ?.let {
                submissionRepository.findByUserIdAndProblemIdAndKindAndDeletedAtIsNullOrderByIdDesc(
                    userId, it.id, SubmissionKind.USER, pageable,
                )
            }
            ?: submissionRepository.findByUserIdAndKindAndDeletedAtIsNullOrderByIdDesc(
                userId, SubmissionKind.USER, pageable,
            )

        // 목록에 필요한 문제 정보만 한 번에 모아 N+1 조회를 피한다.
        val problems: Map<Long, Problem> =
            problemRepository.findAllById(page.content.map { it.problemId }).associateBy { it.id }
        val nickname = nicknameOf(userId)

        return PageResponse.from(
            page.map { submission ->
                SubmissionSummaryResponse.of(
                    submission = submission,
                    problem = problems[submission.problemId],
                    nickname = nickname,
                    // 자기 제출이므로 항상 볼 수 있다.
                    sourceVisible = true,
                )
            },
        )
    }

    private fun validate(problem: Problem, runtimeId: String, sourceCode: String) {
        val runtime = runtimeRegistry.require(runtimeId)
        // 유형이 맞지 않는 런타임은 고를 수 있어도 채점되지 않는다 (#60).
        // 화면이 목록을 걸러 주지만, 화면을 거치지 않는 경로가 생겨도 막히게 여기서도 본다.
        if (runtime.problemKind != problem.problemKind) {
            throw ApiException(
                ErrorCode.RUNTIME_NOT_FOUND,
                "이 문제에서 쓸 수 없는 실행 환경입니다: $runtimeId",
            )
        }
        if (sourceCode.toByteArray().size > properties.maxSourceCodeBytes) {
            throw ApiException(ErrorCode.SOURCE_CODE_TOO_LARGE)
        }
    }

    private companion object {
        const val RUN_WAIT_SECONDS = 30L
    }
}
