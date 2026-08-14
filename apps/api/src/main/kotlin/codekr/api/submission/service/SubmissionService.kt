package codekr.api.submission.service

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.dto.PageResponse
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.properties.SubmissionProperties
import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemKind
import codekr.api.problem.repository.ProblemFileRepository
import codekr.api.problem.repository.ProblemRepository
import codekr.api.problem.service.ProblemService
import codekr.api.queue.JudgeJobFactory
import codekr.api.queue.QueuePublisher
import codekr.api.queue.JudgePriority
import codekr.api.queue.message.JudgeJobMessage
import codekr.api.runtime.RuntimeRegistry
import codekr.api.user.entity.WithdrawnUser
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
import codekr.api.submission.view.SubmissionViewRecorder
import codekr.api.submission.repository.SubmissionSearchCondition
import codekr.api.submission.repository.SubmissionSearchRepository
import codekr.api.submission.repository.SubmissionTestcaseResultRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

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
    private val judgeJobFactory: JudgeJobFactory,
    private val userRepository: UserRepository,
    private val properties: SubmissionProperties,
    private val viewRecorder: SubmissionViewRecorder,
    private val problemFileRepository: ProblemFileRepository,
) {

    /** 임의 입력으로 1회 실행한다. 채점하지 않으므로 제출 이력을 남기지 않는다. */
    fun run(slug: String, request: RunRequest): RunResponse {
        val problem = problemService.requirePublished(slug)
        // **실행(#/run)은 아직 파일 하나다** (#457). 여러 파일을 시험 삼아 돌리는 것은
        // 화면(#498)이 파일 탭을 갖춘 뒤에 열어야 뜻이 있다.
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
        // 파일이 여럿인 문제인지는 **문제와 런타임**이 함께 정한다 (#457) —
        // 같은 문제라도 자바는 Main.java·Helper.java, 파이썬은 main.py·helper.py 다.
        val declared = problemFileRepository
            .findByProblemIdAndRuntimeIdOrderBySeq(problem.id, request.runtimeId)
        val files = SubmissionFiles.resolve(declared, request)
        val sourceCode = files?.let { SubmissionFiles.entrySource(declared, it) } ?: request.sourceCode
        validate(problem, request.runtimeId, sourceCode)
        // 유형마다 채점 대상이 다르다 — SQL 은 정답 쿼리, Redis 은 끝난 뒤의 상태다 (#60, #455).
        if (problem.problemKind.needsTestcases && problem.testcases.isEmpty()) {
            throw ApiException(ErrorCode.TESTCASE_REQUIRED)
        }

        // **검증을 통과한 요청에만 간격을 따진다** (#189). 잘못된 요청을 "너무 잦다" 로
        // 돌려주면 무엇이 틀렸는지 알 수 없고, 고칠 기회도 간격만큼 미뤄진다.
        // 대회 제출은 자기 경로에서 대회가 정한 간격으로 판정한다.
        SubmissionCooldown.require(
            lastSubmittedAt = submissionRepository
                .findFirstByUserIdAndProblemIdAndContestIdIsNullAndDeletedAtIsNullOrderByIdDesc(userId, problem.id)
                ?.createdAt,
            cooldown = SubmissionCooldown.DEFAULT,
            now = Instant.now(),
        )

        val submission = submissionRepository.save(
            Submission(
                userId = userId,
                problemId = problem.id,
                runtimeId = request.runtimeId,
                sourceCode = sourceCode,
                sourceFiles = files,
                totalCount = problem.judgeUnitCount,
                // 요청에 없으면 사용자 기본값을 쓴다 (#104).
                // **서버에서 채운다** — 화면이 기본값을 알고 보내는 방식이면 화면마다 어긋난다.
            ).apply { changeVisibility(request.visibility ?: defaultVisibilityOf(userId)) },
        )

        queuePublisher.publishJudgeJob(
            judgeJobFactory.of(submission, problem),
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

        val sourceVisible = submission.isSourceVisibleTo(principal.userId, principal.isAdmin)
        // 코드를 실제로 본 것만 기록한다 (#136). 작성자가 켜 두지 않았으면 아무 일도 없다.
        viewRecorder.record(submission.userId, submission.id, principal.userId, sourceVisible, principal.isAdmin)

        return SubmissionDetailResponse.of(
            submission = submission,
            // 삭제된 문제라도 이력에는 보여야 하므로 소프트 삭제 여부를 따지지 않고 조회한다.
            problem = problemRepository.findById(submission.problemId).orElse(null),
            results = resultRepository.findBySubmissionIdOrderBySeqAsc(id),
            nickname = nicknameOf(submission.userId),
            sourceVisible = sourceVisible,
            // **보는 사람에게도 알린다** (#136). 기록에 남는다면 보기 전에 그 사실을 알아야 한다.
            //
            // 조건이 record 와 같아야 한다 — 알린 것과 실제로 기록하는 것이 어긋나면
            // 안내가 거짓말이 된다. 작성자 설정 확인이 빠지면서(#199) 제출 상세마다
            // 나가던 사용자 조회 한 번도 함께 사라졌다.
            viewNotified = sourceVisible &&
                submission.userId != principal.userId &&
                !principal.isAdmin,
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
                    nickname = nicknames[submission.userId] ?: WithdrawnUser.LABEL,
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
        WithdrawnUser.nicknameOf(userRepository.findById(userId).orElse(null))

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
        // 파일 하나짜리 제출이 비어 있으면 채점할 것이 없다. 파일 문제에서는 위에서
        // 진입점 파일의 내용이 들어오므로 같은 규칙으로 걸린다 (#457).
        if (sourceCode.isBlank()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "소스 코드는 비어 있을 수 없습니다.")
        }
        val runtime = runtimeRegistry.require(runtimeId)
        // 유형이 맞지 않는 런타임은 고를 수 있어도 채점되지 않는다 (#60).
        // 화면이 목록을 걸러 주지만, 화면을 거치지 않는 경로가 생겨도 막히게 여기서도 본다.
        // 규칙은 `RuntimeDefinition.canSolve` 한 곳에 있다 (#446) — 목록을 거르는 곳과
        // 제출을 막는 곳이 다른 규칙을 쓰면 화면에 보이는데 제출은 안 되는 조합이 생긴다.
        if (!runtime.canSolve(problem.problemKind)) {
            throw ApiException(
                ErrorCode.RUNTIME_NOT_FOUND,
                "이 문제에서 쓸 수 없는 실행 환경입니다: $runtimeId",
            )
        }
        /*
            **이 문제가 허용한 언어인가** (#419).

            화면이 목록을 걸러 주지만 그것으로는 부족하다 — API 를 직접 부르면 통과한다.
            거부 문구에 **무엇을 쓸 수 있는지**를 담는다. 목록이 짧은 것은 고장이 아니라
            출제자의 선택이고, 그 사실을 여기서도 말해야 한다.
        */
        if (!problem.allowsRuntime(runtimeId)) {
            throw ApiException(
                ErrorCode.RUNTIME_NOT_FOUND,
                "이 문제는 ${problem.allowedRuntimeIds.joinToString(", ")} 로만 풀 수 있습니다.",
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
