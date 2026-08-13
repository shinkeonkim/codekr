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
	registry *runtimes.Registry
	// runtimeRegistry 는 런타임 이미지를 받아올 레지스트리다 (#96). 비면 원본에서 받는다.
	runtimeRegistry      string
	box                  sandbox.Sandbox
	compileTimeoutMs     int
	compileMemoryLimitMb int
	maxOutputBytes       int
}

// NewRunner 는 실행 러너를 만든다.
func NewRunner(
	registry *runtimes.Registry,
	box sandbox.Sandbox,
	compileTimeoutMs, compileMemoryLimitMb, maxOutputBytes int,
	runtimeRegistry string,
) *Runner {
	return &Runner{
		registry:             registry,
		runtimeRegistry:      runtimeRegistry,
		box:                  box,
		compileTimeoutMs:     compileTimeoutMs,
		compileMemoryLimitMb: compileMemoryLimitMb,
		maxOutputBytes:       maxOutputBytes,
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

	/*
		함수형 문제 (#421). 하네스가 오면 **하네스를 돌리고 사용자 코드는 그 옆에 둔다.**

		런타임이 그 방법을 모르면 여기서 끊는다 — 조용히 사용자 코드만 돌리면
		"함수만 구현했는데 아무 출력이 없다" 가 되고, 그 이유를 아무도 모른다.
	*/
	run := definition.Run
	sourceFile := definition.SourceFile
	harnessFile := ""
	if job.HarnessSource != "" {
		if !definition.SupportsFunctionHarness() {
			return systemError(job, fmt.Sprintf("이 런타임은 함수형 문제를 지원하지 않습니다: %s", job.RuntimeID))
		}
		run = definition.FunctionHarness.Run
		harnessFile = definition.FunctionHarness.File
		// 하네스가 진입점 자리를 가져가므로 사용자 코드는 다른 이름으로 간다.
		sourceFile = definition.FunctionHarness.SourceFile
	}

	outcome, err := r.box.Run(ctx, sandbox.Spec{
		Image:         definition.ImageRef(r.runtimeRegistry),
		SourceFile:    sourceFile,
		SourceCode:    job.SourceCode,
		HarnessFile:   harnessFile,
		HarnessSource: job.HarnessSource,
		// 여러 파일로 낸 제출 (#457). 비면 SourceCode 하나로 돈다.
		SourceFiles:          job.SourceFiles,
		Stdin:                job.Stdin,
		Compile:              definition.Compile,
		Run:                  run,
		Harness:              definition.Harness,
		User:                 definition.User,
		ExtraFiles:           job.ExtraFiles,
		TimeLimitMs:          job.TimeLimitMs,
		MemoryLimitMb:        job.MemoryLimitMb,
		CompileTimeoutMs:     r.compileTimeoutMs,
		CompileMemoryLimitMb: r.compileMemoryLimitMb,
		MaxOutputBytes:       r.maxOutputBytes,
	})
	if err != nil {
		return systemError(job, err.Error())
	}

	return contract.ExecResult{
		JobID:     job.JobID,
		Status:    statusOf(outcome),
		ExitCode:  outcome.ExitCode,
		Stdout:    outcome.Stdout,
		Stderr:    scrub(outcome.Stderr, harnessFile),
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
