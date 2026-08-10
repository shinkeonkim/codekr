// Package worker 는 실행 큐를 소비해 샌드박스 실행 결과를 응답 스트림으로 돌려준다.
package worker

import (
	"context"
	"fmt"

	"github.com/shinkeonkim/codekr/apps/executor/internal/runtimes"
	"github.com/shinkeonkim/codekr/apps/executor/internal/sandbox"
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// Runner 는 실행 작업 하나를 결과로 바꾼다. 큐/전송과 무관하게 단독으로 테스트할 수 있다.
type Runner struct {
	registry         *runtimes.Registry
	box              sandbox.Sandbox
	compileTimeoutMs int
	maxOutputBytes   int
}

// NewRunner 는 실행 러너를 만든다.
func NewRunner(registry *runtimes.Registry, box sandbox.Sandbox, compileTimeoutMs, maxOutputBytes int) *Runner {
	return &Runner{
		registry:         registry,
		box:              box,
		compileTimeoutMs: compileTimeoutMs,
		maxOutputBytes:   maxOutputBytes,
	}
}

// Run 은 작업을 실행하고 결과를 만든다. 인프라 오류도 결과(SYSTEM_ERROR)로 표현해
// 호출자가 항상 응답을 돌려줄 수 있게 한다.
func (r *Runner) Run(ctx context.Context, job contract.ExecJob) contract.ExecResult {
	definition, found := r.registry.Find(job.RuntimeID)
	if !found {
		return systemError(job, fmt.Sprintf("지원하지 않는 런타임입니다: %s", job.RuntimeID))
	}
	// 손상되거나 버전이 다른 메시지가 그대로 샌드박스 설정이 되지 않게 막는다.
	if err := contract.ValidateLimits(job.TimeLimitMs, job.MemoryLimitMb); err != nil {
		return systemError(job, err.Error())
	}

	outcome, err := r.box.Run(ctx, sandbox.Spec{
		Image:            definition.Image,
		SourceFile:       definition.SourceFile,
		SourceCode:       job.SourceCode,
		Stdin:            job.Stdin,
		Compile:          definition.Compile,
		Run:              definition.Run,
		TimeLimitMs:      job.TimeLimitMs,
		MemoryLimitMb:    job.MemoryLimitMb,
		CompileTimeoutMs: r.compileTimeoutMs,
		MaxOutputBytes:   r.maxOutputBytes,
	})
	if err != nil {
		return systemError(job, err.Error())
	}

	return contract.ExecResult{
		JobID:     job.JobID,
		Status:    statusOf(outcome),
		ExitCode:  outcome.ExitCode,
		Stdout:    outcome.Stdout,
		Stderr:    outcome.Stderr,
		RuntimeMs: outcome.RuntimeMs,
		MemoryKb:  outcome.MemoryKb,
		Truncated: outcome.Truncated,
	}
}

// statusOf 는 샌드박스 관찰 결과를 실행 상태로 옮긴다.
// 판정 우선순위는 컴파일 → 시간 → 메모리 → 런타임 오류 순이다.
func statusOf(outcome sandbox.Outcome) contract.ExecStatus {
	switch {
	case outcome.CompileFailed:
		return contract.StatusCompileError
	case outcome.TimedOut:
		return contract.StatusTimeLimitExceeded
	case outcome.OutOfMemory:
		return contract.StatusMemoryLimitExceeded
	case outcome.Truncated:
		return contract.StatusOutputLimitExceeded
	case outcome.ExitCode != 0:
		return contract.StatusRuntimeError
	default:
		return contract.StatusOK
	}
}

func systemError(job contract.ExecJob, message string) contract.ExecResult {
	return contract.ExecResult{
		JobID:  job.JobID,
		Status: contract.StatusSystemError,
		Stderr: message,
	}
}
