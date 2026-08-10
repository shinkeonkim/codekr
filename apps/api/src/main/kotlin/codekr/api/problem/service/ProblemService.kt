package codekr.api.problem.service

import codekr.api.common.dto.PageResponse
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.dto.ProblemDetailResponse
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
class ProblemService(
    private val problemRepository: ProblemRepository,
    private val problemSearchRepository: ProblemSearchRepository,
    private val runtimeRegistry: RuntimeRegistry,
    private val statsRepository: ProblemStatsRepository,
) {

    fun search(condition: ProblemSearchCondition, pageable: Pageable): PageResponse<ProblemSummaryResponse> {
        val page = problemSearchRepository.search(condition, pageable)
        // 문제마다 질의하면 20건짜리 목록이 21번 조회한다. 한 번에 모아 읽는다.
        val stats = statsRepository.findAll(page.content.map { it.id })
        return PageResponse.from(
            page.map { ProblemSummaryResponse.from(it, stats[it.id] ?: ProblemStats.EMPTY) },
        )
    }

    fun findPublishedDetail(slug: String): ProblemDetailResponse {
        val problem = requirePublished(slug)
        return ProblemDetailResponse.of(problem, runtimeRegistry.findAll(), statsRepository.findOne(problem.id))
    }

    /** 미공개되었거나 삭제된 문제는 존재 자체를 알리지 않는다 — 404 로 응답한다. */
    fun requirePublished(slug: String): Problem =
        problemRepository.findBySlugAndDeletedAtIsNull(slug)?.takeIf { it.published }
            ?: throw ApiException(ErrorCode.PROBLEM_NOT_FOUND)
}
