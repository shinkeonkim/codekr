package codekr.api.problem.controller

import codekr.api.config.security.PublicApi
import codekr.api.common.dto.PageResponse
import codekr.api.problem.dto.ProblemDetailResponse
import codekr.api.problem.dto.ProblemSummaryResponse
import codekr.api.problem.entity.DifficultyState
import codekr.api.problem.entity.DifficultyTier
import codekr.api.auth.security.AuthPrincipal
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.entity.ProblemKind
import codekr.api.problem.repository.ProblemSearchCondition
import codekr.api.problem.repository.RuntimeFilter
import codekr.api.runtime.RuntimeRegistry
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
class ProblemController(
    private val problemService: ProblemService,
    private val runtimeRegistry: RuntimeRegistry,
) {

    @PublicApi
    @GetMapping
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) category: ProblemCategory?,
        @RequestParam(required = false) tier: DifficultyTier?,
        /** 난이도 상태 (#195). 미평가·평가안함은 티어 범위로 잡히지 않는다. */
        @RequestParam(required = false) difficultyState: DifficultyState?,
        /** 태그 주소. 여러 번 넘기면 **모두** 붙은 문제만 나온다 (#232). */
        @RequestParam(required = false) tag: List<String>?,
        /** 채점 방식 (#59). "SQL 문제만" 처럼 고르는 축이다. */
        @RequestParam(required = false) kind: ProblemKind?,
        /** 정답률 범위(%). 티어와 다른 축이다 — 쉬운 문제인데 함정이 있는 것들이 있다. */
        @RequestParam(required = false) acceptanceFrom: Int?,
        @RequestParam(required = false) acceptanceTo: Int?,
        /** 푼 사람 수 범위. "검증된 문제부터" 를 고르는 축이다. */
        @RequestParam(required = false) solversFrom: Int?,
        @RequestParam(required = false) solversTo: Int?,
        /**
         * 해결 여부 (#239). **로그인해야 뜻이 있다** — 비로그인이 넘기면 무시한다.
         * 누를 수 없는 필터를 만들지 않는 것은 화면의 몫이고, 서버는 조용히 넘긴다.
         */
        @RequestParam(required = false) solved: Boolean?,
        /**
         * 언어 (#618). `python` 처럼 **버전 없이** 넘긴다 — 그 언어의 런타임 전부다.
         *
         * 허용 목록이 비어 있는 문제도 함께 걸린다. 그것이 "제한 없음"(#419)의 뜻이다.
         */
        @RequestParam(required = false) language: String?,
        /**
         * 런타임 (#618). `python:3.12` 처럼 버전까지 좁힐 때 쓴다.
         *
         * [language] 와 함께 오면 **이쪽이 이긴다** — 더 좁은 쪽이 사람이 마지막에
         * 고른 것이다.
         */
        @RequestParam(required = false) runtime: String?,
        @RequestParam(defaultValue = "LATEST") sort: ProblemSort,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        principal: AuthPrincipal?,
    ): PageResponse<ProblemSummaryResponse> {
        val condition = ProblemSearchCondition(
            keyword = q,
            category = category,
            tier = tier,
            difficultyState = difficultyState,
            tagSlugs = tag.orEmpty(),
            sort = sort,
            published = true,
            problemKind = kind,
            acceptanceFrom = acceptanceFrom,
            acceptanceTo = acceptanceTo,
            solversFrom = solversFrom,
            solversTo = solversTo,
            viewerId = principal?.userId,
            solved = solved,
            runtimeFilter = RuntimeFilter.of(language, runtime, runtimeRegistry.findAll()),
        )
        return problemService.search(condition, PageRequest.of(maxOf(page, 0), size.coerceIn(1, MAX_PAGE_SIZE)))
    }

    @PublicApi
    @GetMapping("/{slug}")
    fun findOne(@PathVariable slug: String): ProblemDetailResponse = problemService.findPublishedDetail(slug)
}
