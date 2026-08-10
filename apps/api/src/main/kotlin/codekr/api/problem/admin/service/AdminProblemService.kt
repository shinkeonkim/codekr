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
import codekr.api.problem.entity.ProblemKind
import codekr.api.problem.entity.ProblemSqlSpec
import codekr.api.problem.repository.ProblemSqlSpecRepository
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
    private val sqlSpecRepository: ProblemSqlSpecRepository,
) {

    fun search(condition: ProblemSearchCondition, pageable: Pageable): PageResponse<ProblemSummaryResponse> =
        problemSearchRepository.search(condition, pageable).let { page ->
            val stats = statsRepository.findAll(page.content.map { it.id })
            PageResponse.from(page.map { ProblemSummaryResponse.from(it, stats[it.id] ?: ProblemStats.EMPTY) })
        }

    fun findDetail(id: Long): AdminProblemDetailResponse {
        val problem = require(id)
        return AdminProblemDetailResponse.from(
            problem,
            verificationService.findLatest(problem),
            sqlSpecRepository.findById(id).orElse(null),
        )
    }

    /**
     * SQL 스펙을 넣거나 지운다.
     *
     * 유형을 SQL 에서 다른 것으로 바꾸면 스펙을 **지운다.** 남겨 두면 유형을 되돌렸을 때
     * 옛 스키마가 되살아나는데, 그것이 지금 지문과 맞는다는 보장이 없다.
     */
    private fun upsertSqlSpec(problemId: Long, request: ProblemUpsertRequest): ProblemSqlSpec? {
        val spec = request.sqlSpec ?: run {
            sqlSpecRepository.deleteById(problemId)
            return null
        }
        val existing = sqlSpecRepository.findById(problemId).orElse(null)
            ?: return sqlSpecRepository.save(spec.toEntity(problemId))

        existing.schemaSql = spec.schemaSql
        existing.answerSql = spec.answerSql
        existing.ignoreRowOrder = spec.ignoreRowOrder
        return existing
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
        request.sqlSpec?.let { sqlSpecRepository.save(it.toEntity(saved.id)) }
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
        return AdminProblemDetailResponse.from(problem, verificationService.findLatest(problem), upsertSqlSpec(problem.id, request))
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
        // 유형별 자료는 그 유형에만 실린다 (#60). 섞이면 어느 쪽이 진짜인지 알 수 없다.
        if (request.problemKind == ProblemKind.JUDGE_SQL && request.sqlSpec == null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "SQL 문제에는 스키마와 정답 쿼리가 필요합니다.")
        }
        if (request.problemKind != ProblemKind.JUDGE_SQL && request.sqlSpec != null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "SQL 문제가 아닌데 SQL 스펙이 실려 있습니다.")
        }
        // 채점할 대상이 없는 문제는 공개해도 아무 의미가 없다.
        // SQL 문제의 채점 대상은 테스트케이스가 아니라 정답 쿼리다 (#60).
        if (request.published && request.problemKind != ProblemKind.JUDGE_SQL && request.testcases.isEmpty()) {
            throw ApiException(ErrorCode.TESTCASE_REQUIRED)
        }

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
