package codekr.api.problem.repository

import codekr.api.problem.entity.DifficultyState
import codekr.api.problem.entity.DifficultyTier
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.entity.ProblemKind

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
    /** 난이도 상태 (#195). 미평가·평가안함은 티어 범위로 잡히지 않는다. */
    val difficultyState: DifficultyState? = null,
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

    /** 채점 방식 (#59). "SQL 문제만" 처럼 고를 수 있어야 한다. */
    val problemKind: ProblemKind? = null,

    /**
     * 정답률 범위 (0~100). 티어와 **다른 축이다** — 쉬운 문제인데 함정이 있는 것들이 있다.
     *
     * 제출자가 없는 문제는 정답률이 `NULL` 이라 어느 범위에도 들지 않는다 (#205) —
     * `0/0` 은 0% 가 아니다.
     */
    val acceptanceFrom: Int? = null,
    val acceptanceTo: Int? = null,

    /** 푼 사람 수 범위. "검증된 문제부터" 를 고르는 축이다. */
    val solversFrom: Int? = null,
    val solversTo: Int? = null,

    /**
     * 해결 여부로 거르는 사람. **비로그인이면 null 이고, 그때 이 필터는 없다** —
     * 누를 수 없는 필터를 보여 주지 않는다.
     */
    val viewerId: Long? = null,

    /** `true` 면 푼 것만, `false` 면 안 푼 것만. `viewerId` 가 있을 때만 뜻이 있다. */
    val solved: Boolean? = null,

    /**
     * 언어·런타임 (#618). **두 단으로 고른다** — 언어로 크게, 원하면 런타임까지 좁힌다.
     *
     * 전에는 이 자리에 "허용 언어 필터는 두지 않았다" 는 주석이 있었다. 이유는
     * **"문제별 허용 언어라는 것이 없다"** 였고 그때는 맞았다 — 그 데이터를 #419 가
     * 나중에 만들었다. #239 가 항목을 적어 두고도 빠뜨린 자리가 여기다.
     *
     * 걸 조건은 [RuntimeFilter] 가 정한다. **"제한 없음" 문제를 함께 거는 것**이
     * 이 필터의 전부다.
     */
    val runtimeFilter: RuntimeFilter? = null,
)
