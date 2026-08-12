package codekr.api.problem.repository

import codekr.api.problem.entity.DifficultyTier
import codekr.api.problem.entity.ProblemCategory

enum class ProblemSort { LATEST, TITLE, DIFFICULTY }

data class ProblemSearchCondition(
    val keyword: String? = null,
    val category: ProblemCategory? = null,
    val tier: DifficultyTier? = null,
    /**
     * 태그 주소 목록 (#232). 여러 개면 **그리고**로 건다.
     *
     * 또는(OR)로 하면 태그를 고를수록 결과가 넓어져 필터 구실을 못 한다. 고르는 목적은
     * 좁히는 것이다 — "DP 이면서 이분 탐색" 을 찾는 사람은 있어도, "DP 이거나 아무거나"
     * 를 찾는 사람은 없다.
     */
    val tagSlugs: List<String> = emptyList(),
    val sort: ProblemSort = ProblemSort.LATEST,
    /** null 이면 공개 여부와 무관하게 조회한다 (어드민 목록). */
    val published: Boolean? = true,
)
