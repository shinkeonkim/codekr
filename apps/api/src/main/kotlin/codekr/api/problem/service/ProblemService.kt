package codekr.api.problem.service

import codekr.api.common.dto.PageResponse
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.dto.ProblemDetailResponse
import codekr.api.problem.dto.ProblemStats
import codekr.api.problem.dto.ProblemSummaryResponse
import codekr.api.problem.repository.ProblemStatsRepository
import codekr.api.problem.entity.Problem
import codekr.api.problem.repository.ProblemFileRepository
import codekr.api.problem.repository.ProblemRepository
import codekr.api.problem.repository.ProblemSearchCondition
import codekr.api.problem.repository.ProblemSearchRepository
import codekr.api.runtime.RuntimeRegistry
import codekr.api.tag.service.TagService
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ProblemService(
    private val fileRepository: ProblemFileRepository,
    private val creditService: codekr.api.problem.credit.ProblemCreditService,
    private val problemRepository: ProblemRepository,
    private val problemSearchRepository: ProblemSearchRepository,
    private val runtimeRegistry: RuntimeRegistry,
    private val statsRepository: ProblemStatsRepository,
    private val tagService: TagService,
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
        // 이 문제의 유형으로 풀 수 있는 실행 환경만 내린다 (#60).
        // 전체를 내리면 화면이 SQL 문제에 파이썬을 권하게 된다.
        //
        // **문제가 언어를 좁혀 두었으면 그것까지 본다** (#419). 비어 있으면 종류 전부다.
        return ProblemDetailResponse.of(
            problem,
            runtimeRegistry.findFor(problem.problemKind).filter { problem.allowsRuntime(it.id) },
            statsRepository.findOne(problem.id),
            tagService.tagsOf(problem.id),
            creditService.creditsOf(problem.id),
            // 파일 목록은 런타임마다다 (#457). 한 번에 읽어 언어별로 나눈다 —
            // 언어마다 질의하면 목록이 긴 문제에서 조회가 언어 수만큼 는다.
            fileRepository.findByProblemIdOrderBySeq(problem.id).groupBy { it.runtimeId },
        )
    }

    /**
     * 미공개되었거나 삭제된 문제는 존재 자체를 알리지 않는다 — 404 로 응답한다.
     *
     * **번호로도 연다** (#204). 사람은 문제를 "1000번" 이라고 부르는데, 그렇게 부른 것을
     * 주소창에 넣었을 때 열리지 않으면 번호를 보여 주는 뜻이 없다.
     *
     * slug 는 그대로 정본이다 — 검색 결과에 `두-수의-합` 이 뜨는 것과 `1000` 이 뜨는
     * 것은 다르고, 이미 공유된 링크도 slug 다.
     */
    fun requirePublished(key: String): Problem =
        findByKey(key)?.takeIf { it.published } ?: throw ApiException(ErrorCode.PROBLEM_NOT_FOUND)

    /**
     * 숫자면 번호로, 아니면 slug 로 찾는다.
     *
     * **숫자로만 이루어진 slug 는 만들 수 없다** — 어드민 검증이 slug 에 글자를 요구하지
     * 않으므로, 그런 slug 가 생기면 번호와 부딪힌다. 그때는 번호가 이긴다(먼저 본다).
     * 지금 그런 문제는 없고, 생기면 이 주석이 그것을 설명한다.
     */
    private fun findByKey(key: String): Problem? {
        val id = key.toLongOrNull()
        if (id != null) {
            problemRepository.findByIdAndDeletedAtIsNull(id)?.let { return it }
        }
        return problemRepository.findBySlugAndDeletedAtIsNull(key)
    }
}
