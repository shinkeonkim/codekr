package codekr.api.problem.controller

import codekr.api.common.dto.PageResponse
import codekr.api.problem.dto.ProblemDetailResponse
import codekr.api.problem.dto.ProblemSummaryResponse
import codekr.api.problem.entity.DifficultyTier
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.repository.ProblemSearchCondition
import codekr.api.problem.repository.ProblemSort
import codekr.api.problem.service.ProblemService
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val MAX_PAGE_SIZE = 100

@RestController
@RequestMapping("/api/v1/problems")
class ProblemController(private val problemService: ProblemService) {

    @GetMapping
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) category: ProblemCategory?,
        @RequestParam(required = false) tier: DifficultyTier?,
        /** 태그 주소. 여러 번 넘기면 **모두** 붙은 문제만 나온다 (#232). */
        @RequestParam(required = false) tag: List<String>?,
        @RequestParam(defaultValue = "LATEST") sort: ProblemSort,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<ProblemSummaryResponse> {
        val condition = ProblemSearchCondition(q, category, tier, tag.orEmpty(), sort, published = true)
        return problemService.search(condition, PageRequest.of(maxOf(page, 0), size.coerceIn(1, MAX_PAGE_SIZE)))
    }

    @GetMapping("/{slug}")
    fun findOne(@PathVariable slug: String): ProblemDetailResponse = problemService.findPublishedDetail(slug)
}
