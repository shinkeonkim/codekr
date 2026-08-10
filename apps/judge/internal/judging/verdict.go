package judging

import contract "github.com/shinkeonkim/codekr/libs/gocontract"

// VerdictOf 는 실행 결과 하나를 테스트케이스 판정으로 옮긴다.
// 정답 비교는 실행이 정상으로 끝난 경우에만 의미가 있다.
func VerdictOf(result contract.ExecResult, expectedOutput string) contract.Verdict {
	switch result.Status {
	case contract.StatusOK:
		if OutputMatches(result.Stdout, expectedOutput) {
			return contract.VerdictAccepted
		}
		return contract.VerdictWrongAnswer
	case contract.StatusTimeLimitExceeded:
		return contract.VerdictTimeLimitExceeded
	case contract.StatusMemoryLimitExceeded:
		return contract.VerdictMemoryLimitExceeded
	case contract.StatusCompileError:
		return contract.VerdictCompileError
	case contract.StatusOutputLimitExceeded:
		return contract.VerdictOutputLimitExceeded
	case contract.StatusRuntimeError:
		return contract.VerdictRuntimeError
	default:
		return contract.VerdictSystemError
	}
}

// Summary 는 테스트케이스 판정들을 제출 단위 결과로 집계한다.
type Summary struct {
	Verdict      contract.Verdict
	PassedCount  int
	TotalCount   int
	MaxRuntimeMs int
	MaxMemoryKb  int
}

// Accumulator 는 테스트케이스 결과를 하나씩 받아 최종 집계를 만든다.
type Accumulator struct {
	total   int
	passed  int
	maxTime int
	maxMem  int
	// firstFailure 는 처음 만난 실패 판정이다. 최종 판정은 이 값으로 정한다.
	firstFailure contract.Verdict
}

// NewAccumulator 는 전체 테스트케이스 수를 알고 집계를 시작한다.
func NewAccumulator(total int) *Accumulator { return &Accumulator{total: total} }

// Add 는 테스트케이스 하나의 결과를 반영한다.
func (a *Accumulator) Add(verdict contract.Verdict, runtimeMs, memoryKb int) {
	if verdict == contract.VerdictAccepted {
		a.passed++
	} else if a.firstFailure == "" {
		a.firstFailure = verdict
	}
	if runtimeMs > a.maxTime {
		a.maxTime = runtimeMs
	}
	if memoryKb > a.maxMem {
		a.maxMem = memoryKb
	}
}

// Summarize 는 집계 결과를 돌려준다.
func (a *Accumulator) Summarize() Summary {
	verdict := contract.VerdictAccepted
	if a.firstFailure != "" {
		verdict = a.firstFailure
	}
	return Summary{
		Verdict:      verdict,
		PassedCount:  a.passed,
		TotalCount:   a.total,
		MaxRuntimeMs: a.maxTime,
		MaxMemoryKb:  a.maxMem,
	}
}
