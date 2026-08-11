package codekr.api.problem.admin.service

import codekr.api.common.dto.PageResponse
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.admin.dto.AdminProblemDetailResponse
import codekr.api.problem.admin.dto.ProblemCreatedResponse
import codekr.api.problem.admin.dto.ProblemUpsertRequest
import codekr.api.problem.admin.dto.RuntimeLimitRequest
import codekr.api.problem.admin.dto.TemplateRequest
import codekr.api.problem.admin.dto.TestcaseRequest
import codekr.api.problem.dto.ProblemStats
import codekr.api.problem.dto.ProblemSummaryResponse
import codekr.api.problem.repository.ProblemStatsRepository
import codekr.api.problem.entity.Problem
import codekr.api.problem.repository.ProblemRepository
import codekr.api.problem.repository.ProblemSearchCondition
import codekr.api.problem.repository.ProblemSearchRepository
import codekr.api.runtime.RuntimeRegistry
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AdminProblemService(
    private val problemRepository: ProblemRepository,
    private val problemSearchRepository: ProblemSearchRepository,
    private val runtimeRegistry: RuntimeRegistry,
    private val verificationService: SolutionVerificationService,
    private val statsRepository: ProblemStatsRepository,
) {

    fun search(condition: ProblemSearchCondition, pageable: Pageable): PageResponse<ProblemSummaryResponse> =
        problemSearchRepository.search(condition, pageable).let { page ->
            val stats = statsRepository.findAll(page.content.map { it.id })
            PageResponse.from(page.map { ProblemSummaryResponse.from(it, stats[it.id] ?: ProblemStats.EMPTY) })
        }

    fun findDetail(id: Long): AdminProblemDetailResponse {
        val problem = require(id)
        return AdminProblemDetailResponse.from(problem, verificationService.findLatest(problem))
    }

    @Transactional
    fun create(request: ProblemUpsertRequest, createdBy: Long): ProblemCreatedResponse {
        if (problemRepository.existsBySlugAndDeletedAtIsNull(request.slug)) {
            throw ApiException(ErrorCode.SLUG_ALREADY_EXISTS)
        }
        validate(request)

        val problem = Problem(
            slug = request.slug,
            title = request.title,
            category = request.category,
            problemKind = request.problemKind,
            difficultyLevel = request.difficulty.level,
            description = request.description,
            inputDescription = request.inputDescription,
            outputDescription = request.outputDescription,
            timeLimitMs = request.timeLimitMs,
            memoryLimitMb = request.memoryLimitMb,
            judgePriority = request.judgePriority,
            published = request.published,
            createdBy = createdBy,
        ).apply {
            addTestcases(request.testcases.map(TestcaseRequest::toEntity))
            addTemplates(request.templates.map(TemplateRequest::toEntity))
            addRuntimeLimits(request.runtimeLimits.map(RuntimeLimitRequest::toEntity))
            replaceSolution(request.solution?.runtimeId, request.solution?.sourceCode)
        }

        val saved = problemRepository.save(problem)
        return ProblemCreatedResponse(saved.id, saved.slug)
    }

    @Transactional
    fun update(id: Long, request: ProblemUpsertRequest): AdminProblemDetailResponse {
        val problem = require(id)
        if (problem.slug != request.slug && problemRepository.existsBySlugAndDeletedAtIsNull(request.slug)) {
            throw ApiException(ErrorCode.SLUG_ALREADY_EXISTS)
        }
        validate(request)

        problem.apply {
            slug = request.slug
            title = request.title
            category = request.category
            problemKind = request.problemKind
            difficulty = request.difficulty
            description = request.description
            inputDescription = request.inputDescription
            outputDescription = request.outputDescription
            timeLimitMs = request.timeLimitMs
            memoryLimitMb = request.memoryLimitMb
            judgePriority = request.judgePriority
            published = request.published
            softDeleteTestcases()
            softDeleteTemplates()
            softDeleteRuntimeLimits()
        }
        // 살아 있는 행에만 걸린 유니크 인덱스 때문에, 기존 행의 삭제 표시가 먼저 반영돼야 한다.
        problemRepository.flush()

        problem.addTestcases(request.testcases.map(TestcaseRequest::toEntity))
        problem.addTemplates(request.templates.map(TemplateRequest::toEntity))
        problem.addRuntimeLimits(request.runtimeLimits.map(RuntimeLimitRequest::toEntity))
        problem.replaceSolution(request.solution?.runtimeId, request.solution?.sourceCode)
        return AdminProblemDetailResponse.from(problem, verificationService.findLatest(problem))
    }

    /**
     * 소프트 삭제한다. 물리 삭제하지 않으므로 이 문제로 제출한 이력은 그대로 남는다.
     */
    @Transactional
    fun delete(id: Long) = require(id).delete()

    private fun require(id: Long): Problem =
        problemRepository.findByIdAndDeletedAtIsNull(id) ?: throw ApiException(ErrorCode.PROBLEM_NOT_FOUND)

    private fun validate(request: ProblemUpsertRequest) {
        // 채점기 구현도 스펙 테이블도 없는 유형으로는 문제를 만들 수 없다 (#59).
        // 허용하면 채점되지 않는 문제가 만들어지고, 그 사실은 누가 제출한 뒤에야 드러난다.
        if (!request.problemKind.ready) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "아직 지원하지 않는 문제 유형입니다: ${request.problemKind.label}",
            )
        }
        // 채점할 대상이 없는 문제는 공개해도 아무 의미가 없다.
        if (request.published && request.testcases.isEmpty()) throw ApiException(ErrorCode.TESTCASE_REQUIRED)

        if (request.testcases.groupingBy { it.seq }.eachCount().any { it.value > 1 }) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "테스트케이스 순번이 중복되었습니다.")
        }
        if (request.templates.groupingBy { it.runtimeId }.eachCount().any { it.value > 1 }) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "같은 실행 환경의 초기 코드가 중복되었습니다.")
        }
        request.templates.firstOrNull { !runtimeRegistry.exists(it.runtimeId) }?.let {
            throw ApiException(ErrorCode.RUNTIME_NOT_FOUND, "지원하지 않는 실행 환경입니다: ${it.runtimeId}")
        }
        request.solution?.let { runtimeRegistry.require(it.runtimeId) }
    }
}
