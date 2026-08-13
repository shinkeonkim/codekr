package codekr.api.scaling.dto

import jakarta.validation.constraints.Min

data class ScaleRequest(@field:Min(0) val replicas: Int)

/**
 * 워커 수 조정 (#390).
 *
 * **최소가 1 이다.** 0 이면 그 차선의 채점이 통째로 멈추는데, 화면에서 그것은 "적체"
 * 로 보인다 — 원인이 조정이라는 것을 아무도 모른다.
 */
data class WorkerRequest(@field:Min(1) val workers: Int)
