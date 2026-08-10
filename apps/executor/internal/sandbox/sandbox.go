// Package sandbox 는 신뢰할 수 없는 코드를 격리 환경에서 실행하는 경계를 정의한다.
//
// 구현은 컨테이너 런타임에 종속적이므로 이 인터페이스 뒤에 가둔다 (ADR-0003).
// 이 파일 밖의 코드는 어떤 런타임을 쓰는지 알지 못한다.
package sandbox

import "context"

// Spec 은 실행 1회에 필요한 모든 입력이다.
type Spec struct {
	Image         string
	SourceFile    string
	SourceCode    string
	Stdin         string
	Compile       []string
	Run           []string
	TimeLimitMs   int
	MemoryLimitMb int
	// 컴파일 단계에 허용하는 최대 시간. 문제의 시간 제한과 별개로 적용된다.
	CompileTimeoutMs int
	MaxOutputBytes   int
}

// Outcome 은 샌드박스가 관찰한 실행 결과다. 정답 여부는 판단하지 않는다.
type Outcome struct {
	// ExitCode 는 사용자 프로그램의 종료 코드다.
	ExitCode int
	// TimedOut 은 시간 제한 초과로 강제 종료됐음을 뜻한다.
	TimedOut bool
	// OutOfMemory 는 메모리 제한으로 커널에 의해 종료됐음을 뜻한다.
	OutOfMemory bool
	// CompileFailed 는 컴파일 단계에서 실패했음을 뜻한다.
	CompileFailed bool
	Stdout        string
	Stderr        string
	RuntimeMs     int
	MemoryKb      int
	Truncated     bool
}

// Sandbox 는 코드 한 벌을 격리 실행한다.
type Sandbox interface {
	Run(ctx context.Context, spec Spec) (Outcome, error)
	// Close 는 백그라운드 자원을 정리한다.
	Close() error
}
