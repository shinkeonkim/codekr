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
import codekr.api.ranking.service.ProblemScoreResyncService
import codekr.api.tag.service.TagService
import codekr.api.problem.entity.DifficultyState
import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemKind
import codekr.api.problem.entity.ProblemSqlSpec
import codekr.api.problem.repository.ProblemSqlSpecRepository
import codekr.api.problem.repository.ProblemRepository
import codekr.api.problem.repository.ProblemSearchCondition
import codekr.api.problem.repository.ProblemSearchRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AdminProblemService(
    private val creditService: codekr.api.problem.credit.ProblemCreditService,
    private val problemRepository: ProblemRepository,
    private val problemSearchRepository: ProblemSearchRepository,
    private val verificationService: SolutionVerificationService,
    private val statsRepository: ProblemStatsRepository,
    private val tagService: TagService,
    private val sqlSpecRepository: ProblemSqlSpecRepository,
    private val scoreResyncService: ProblemScoreResyncService,
    private val validator: ProblemUpsertValidator,
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
            tagService.tagsOf(id),
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
        validator.validate(request)

        val problem = Problem(
            slug = request.slug,
            title = request.title,
            category = request.category,
            problemKind = request.problemKind,
            difficultyLevel = request.difficulty?.level,
            difficultyState = resolveState(request),
            description = request.description,
            inputDescription = request.inputDescription,
            outputDescription = request.outputDescription,
            timeLimitMs = request.timeLimitMs,
            memoryLimitMb = request.memoryLimitMb,
            judgePriority = request.judgePriority,
            outputComparison = request.outputComparison,
            floatEpsilon = request.floatEpsilon,
            published = request.published,
            createdBy = createdBy,
            sourceLabel = request.sourceLabel?.trim()?.takeIf { it.isNotBlank() },
            sourceUrl = request.sourceUrl?.trim()?.takeIf { it.isNotBlank() },
        ).apply {
            addTestcases(request.testcases.map(TestcaseRequest::toEntity))
            addTemplates(request.templates.map(TemplateRequest::toEntity))
            addRuntimeLimits(request.runtimeLimits.map(RuntimeLimitRequest::toEntity))
            replaceAllowedRuntimes(request.allowedRuntimeIds)
            checkerSource = request.checkerSource?.takeIf { it.isNotBlank() }
            replaceSolution(request.solution?.runtimeId, request.solution?.sourceCode)
        }

        val saved = problemRepository.save(problem)
        request.sqlSpec?.let { sqlSpecRepository.save(it.toEntity(saved.id)) }
        creditService.replace(saved.id, request.setterIds, request.reviewerIds)
        return ProblemCreatedResponse(saved.id, saved.slug)
    }

    @Transactional
    fun update(id: Long, request: ProblemUpsertRequest): AdminProblemDetailResponse {
        val problem = require(id)
        if (problem.slug != request.slug && problemRepository.existsBySlugAndDeletedAtIsNull(request.slug)) {
            throw ApiException(ErrorCode.SLUG_ALREADY_EXISTS)
        }
        validator.validate(request)

        problem.apply {
            slug = request.slug
            title = request.title
            category = request.category
            problemKind = request.problemKind
            difficultyLevel = request.difficulty?.level
            difficultyState = resolveState(request)
            description = request.description
            inputDescription = request.inputDescription
            outputDescription = request.outputDescription
            timeLimitMs = request.timeLimitMs
            memoryLimitMb = request.memoryLimitMb
            judgePriority = request.judgePriority
            outputComparison = request.outputComparison
            floatEpsilon = request.floatEpsilon
            published = request.published
            sourceLabel = request.sourceLabel?.trim()?.takeIf { it.isNotBlank() }
            sourceUrl = request.sourceUrl?.trim()?.takeIf { it.isNotBlank() }
            softDeleteTestcases()
            softDeleteTemplates()
            softDeleteRuntimeLimits()
        }
        // 살아 있는 행에만 걸린 유니크 인덱스 때문에, 기존 행의 삭제 표시가 먼저 반영돼야 한다.
        problemRepository.flush()

        problem.addTestcases(request.testcases.map(TestcaseRequest::toEntity))
        problem.addTemplates(request.templates.map(TemplateRequest::toEntity))
        problem.addRuntimeLimits(request.runtimeLimits.map(RuntimeLimitRequest::toEntity))
        // 허용 목록은 통째로 교체한다 (#419). 소프트 삭제가 없으므로 flush 순서를 타지 않는다.
        problem.replaceAllowedRuntimes(request.allowedRuntimeIds)
        problem.checkerSource = request.checkerSource?.takeIf { it.isNotBlank() }
        problem.replaceSolution(request.solution?.runtimeId, request.solution?.sourceCode)

        /*
            바뀐 난이도·공개 여부를 이미 맞힌 사람들의 점수에 반영한다 (#194).

            **바뀌었는지 따지지 않고 늘 부른다.** 값이 그대로면 갱신되는 행이 없으므로
            비용이 거의 없고, "어떤 항목이 점수에 영향을 주는가" 를 두 곳에서 관리하지
            않아도 된다 — 그 목록이 어긋나면 다시 지금 같은 구멍이 생긴다.

            위의 flush 로 바뀐 값이 이미 표에 있다. 그전에 부르면 **옛 난이도**로 계산한다.
        */
        scoreResyncService.resync(problem.id)
        creditService.replace(problem.id, request.setterIds, request.reviewerIds)

        // 태그는 이 요청으로 바뀌지 않지만, 응답에서 빠지면 편집 화면이 저장 직후 태그를
        // 잃어버린 것처럼 보인다.
        return AdminProblemDetailResponse.from(
            problem,
            verificationService.findLatest(problem),
            upsertSqlSpec(problem.id, request),
            tagService.tagsOf(problem.id),
            creditService.creditsOf(problem.id),
        )
    }

    /**
     * 소프트 삭제한다. 물리 삭제하지 않으므로 이 문제로 제출한 이력은 그대로 남는다.
     */
    @Transactional
    fun delete(id: Long) {
        require(id).delete()
        // 지운 문제의 점수는 빠져야 한다 (#194). 삭제 표시가 표에 닿은 뒤에 다시 센다.
        problemRepository.flush()
        scoreResyncService.resync(id)
    }

    /**
     * 난이도와 상태를 맞춘다 (#195).
     *
     * **난이도를 골랐으면 RATED 다.** 화면이 둘을 각각 보내므로 어긋난 조합이 올 수
     * 있는데(난이도를 고르고 상태는 미평가), 그때는 고른 값이 이긴다 — 사람이 마지막에
     * 한 행동이 그것이다.
     */
    private fun resolveState(request: ProblemUpsertRequest): DifficultyState =
        if (request.difficulty != null) DifficultyState.RATED
        else request.difficultyState.takeIf { !it.scored } ?: DifficultyState.UNRATED

    private fun require(id: Long): Problem =
        problemRepository.findByIdAndDeletedAtIsNull(id) ?: throw ApiException(ErrorCode.PROBLEM_NOT_FOUND)
}
