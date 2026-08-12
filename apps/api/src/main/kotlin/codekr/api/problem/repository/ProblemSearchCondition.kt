package codekr.api.problem.repository

import codekr.api.problem.entity.DifficultyTier
import codekr.api.problem.entity.ProblemCategory

/**
 * 문제 목록의 정렬 기준 (#132, #205).
 *
 * **방향을 값에 담는다.** 기준과 방향을 두 파라미터로 나누면 "제목순 내림차순" 처럼
 * 뜻이 옅은 조합이 생기고, 주소에 남는 상태도 둘이 된다 (#132 가 정한 규칙).
 *
 * #132 는 "기준마다 자연스러운 방향 하나만" 이라고 정했었다. **그 판단을 뒤집는다** —
 * 많이 풀린 순과 적게 풀린 순은 찾는 목적이 다르고 둘 다 쓸 일이 있다.
 */
enum class ProblemSort {
    LATEST,
    OLDEST,
    TITLE,
    DIFFICULTY,
    DIFFICULTY_DESC,

    /** 많이 풀린 순. 검증된 문제부터 풀고 싶을 때. */
    SOLVERS_DESC,

    /** 적게 풀린 순. 아무도 안 푼 것을 찾을 때. */
    SOLVERS_ASC,

    /** 정답률 높은 순. 지금 실력에서 풀리는 것을 찾을 때. */
    ACCEPTANCE_DESC,

    /** 정답률 낮은 순. 난이도와는 다른 축이다 — 쉬운 문제인데 함정이 있는 것들이 여기 온다. */
    ACCEPTANCE_ASC,
}

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
