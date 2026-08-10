package codekr.api.problem.entity

/**
 * 문제별 실행 제약의 허용 범위.
 *
 * Go 쪽 `libs/gocontract/limits.go` 와 같은 값을 유지해야 한다 — 한쪽만 바뀌면
 * api 가 받아들인 문제가 실행 직전에 거부된다 (docs/06_실행_제약_계약.md).
 */
object ExecutionLimits {
    const val MIN_TIME_LIMIT_MS = 100
    const val MAX_TIME_LIMIT_MS = 30_000
    const val DEFAULT_TIME_LIMIT_MS = 2_000

    const val MIN_MEMORY_LIMIT_MB = 16
    const val MAX_MEMORY_LIMIT_MB = 2_048
    const val DEFAULT_MEMORY_LIMIT_MB = 256
}
