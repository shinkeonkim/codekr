package codekr.api.problem.service

import codekr.api.common.dto.PageResponse
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.dto.ProblemDetailResponse
import codekr.api.problem.dto.ProblemSummaryResponse
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
) {

    fun search(condition: ProblemSearchCondition, pageable: Pageable): PageResponse<ProblemSummaryResponse> =
        PageResponse.from(problemSearchRepository.search(condition, pageable).map(ProblemSummaryResponse::from))

    fun findPublishedDetail(slug: String): ProblemDetailResponse =
        ProblemDetailResponse.of(requirePublished(slug), runtimeRegistry.findAll())

    /** 미공개되었거나 삭제된 문제는 존재 자체를 알리지 않는다 — 404 로 응답한다. */
    fun requirePublished(slug: String): Problem =
        problemRepository.findBySlugAndDeletedAtIsNull(slug)?.takeIf { it.published }
            ?: throw ApiException(ErrorCode.PROBLEM_NOT_FOUND)
}
