package contract

import "fmt"

// 문제별 실행 제약의 허용 범위. api(Kotlin)의 검증 규칙과 같은 값을 유지해야 한다
// — 어느 한쪽만 바뀌면 큐를 통과한 작업이 실행 직전에 거부된다 (docs/06_실행_제약_계약.md).
const (
	MinTimeLimitMs   = 100
	MaxTimeLimitMs   = 30_000
	MinMemoryLimitMb = 16
	MaxMemoryLimitMb = 2048
)

// ValidateLimits 는 실행 제약이 허용 범위 안에 있는지 확인한다.
//
// 큐 메시지는 신뢰할 수 없는 입력으로 다룬다. 발행하는 쪽(api)이 이미 검증하더라도,
// 버전이 다른 발행자나 손상된 메시지가 그대로 샌드박스 설정으로 넘어가면
// 컨테이너가 만들어지지 않거나(0 바이트 메모리) 워커를 오래 붙잡는다.
func ValidateLimits(timeLimitMs, memoryLimitMb int) error {
	if timeLimitMs < MinTimeLimitMs || timeLimitMs > MaxTimeLimitMs {
		return fmt.Errorf(
			"시간 제한이 허용 범위를 벗어났습니다: %dms (허용 %d~%dms)",
			timeLimitMs, MinTimeLimitMs, MaxTimeLimitMs,
		)
	}
	if memoryLimitMb < MinMemoryLimitMb || memoryLimitMb > MaxMemoryLimitMb {
		return fmt.Errorf(
			"메모리 제한이 허용 범위를 벗어났습니다: %dMB (허용 %d~%dMB)",
			memoryLimitMb, MinMemoryLimitMb, MaxMemoryLimitMb,
		)
	}
	return nil
}
