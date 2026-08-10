package codekr.api.problem.entity

/**
 * 특정 런타임으로 실행할 때 실제로 적용되는 제한 (#97).
 *
 * [overridden] 은 화면에서 "이 언어는 별도 제한" 을 알려주기 위한 것이다.
 * 값만 보여주면 왜 문제 상세의 숫자와 다른지 알 수 없다.
 */
data class ResolvedLimits(
    val timeLimitMs: Int,
    val memoryLimitMb: Int,
    val overridden: Boolean,
)
