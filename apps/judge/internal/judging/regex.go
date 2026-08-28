package judging

import (
	"context"
	"log/slog"
	"strings"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// 정규식 실행 환경 (#653). 새 이미지를 만들지 않고 이미 등록된 파이썬을 쓴다.
const defaultRegexRuntimeID = "regex:python"

/*
RegexJudge 는 정규식 문제를 채점한다 (#653).

**제출이 코드가 아니라 패턴 하나다.** 그래서 하네스가 그것을 *실행*하지 않고
**자료로 읽어** 엔진에 넘긴다 — 실행했다가는 제출이 곧 임의 코드 실행이 된다.

**정답 패턴을 두지 않는다.** SQL·Redis 는 정답을 돌려 기대값을 만들지만, 여기서는
"이 줄은 맞아야 한다" 가 곧 기대값이다. 정답 패턴으로 기대값을 만들면 **출제자가
실수한 패턴이 그대로 정답이 되어** 아무도 그것을 잡을 수 없다.

바꾸지 않은 것: 하네스의 출력 형식과 비교 함수다. `--- codekr:expected` /
`--- codekr:actual` 로 나뉜 줄을 내므로 견주는 방법이 다른 유형과 같다.
*/
type RegexJudge struct {
	executor ExecutorClient
	log      *slog.Logger
}

// NewRegexJudge 는 정규식 채점기를 만든다.
func NewRegexJudge(executor ExecutorClient, log *slog.Logger) *RegexJudge {
	return &RegexJudge{executor: executor, log: log}
}

// Judge 는 패턴을 확인 문자열들에 돌려 기대 판정과 견준다.
func (j *RegexJudge) Judge(ctx context.Context, job contract.JudgeJob, emit Emitter) Outcome {
	emit(contract.Event{Type: contract.EventJudging, SubmissionID: job.SubmissionID, TotalCount: 1})

	if job.Regex == nil {
		// 스펙 없이 정규식 문제가 큐에 들어왔다. 짐작해 채점하지 않는다.
		j.log.Error("정규식 문제인데 스펙이 없습니다", "submissionId", job.SubmissionID)
		return Outcome{Summary: Summary{Verdict: contract.VerdictSystemError, TotalCount: 1}}
	}

	result, err := j.executor.Run(ctx, contract.ExecJob{
		RuntimeID:     regexRuntimeOf(job),
		SourceCode:    job.SourceCode,
		TimeLimitMs:   job.TimeLimitMs,
		MemoryLimitMb: job.MemoryLimitMb,
		ExtraFiles:    regexFiles(job.Regex),
	})
	if err != nil {
		j.log.Error("실행 요청 실패", "submissionId", job.SubmissionID, "error", err)
		result = contract.ExecResult{Status: contract.StatusSystemError, Stderr: err.Error()}
	}

	verdict, detail := j.verdictOf(result)
	emit(contract.Event{
		Type:          contract.EventTestcase,
		SubmissionID:  job.SubmissionID,
		Seq:           1,
		Verdict:       verdict,
		RuntimeMs:     result.RuntimeMs,
		MemoryKb:      result.MemoryKb,
		StderrExcerpt: excerpt(detail),
	})

	summary := Summary{
		Verdict:      verdict,
		TotalCount:   1,
		MaxRuntimeMs: result.RuntimeMs,
		MaxMemoryKb:  result.MemoryKb,
	}
	if verdict == contract.VerdictAccepted {
		summary.PassedCount = 1
	}
	// 문법이 틀린 패턴은 컴파일 오류로 대응한다 — SQL·Redis 와 같은 판단이다.
	if verdict == contract.VerdictCompileError {
		return Outcome{Summary: summary, CompileError: excerpt(detail)}
	}
	return Outcome{Summary: summary}
}

func (j *RegexJudge) verdictOf(result contract.ExecResult) (verdict contract.Verdict, detail string) {
	if result.Status != contract.StatusOK && result.Status != contract.StatusRuntimeError {
		/*
			**재앙적 백트래킹은 여기서 시간 초과로 온다** (#653).

			`(a+)+$` 같은 패턴은 실제로 매달리고, 샌드박스가 컨테이너째로 끊는다.
			그것이 정확한 답이다 — 그 패턴은 실제로 느리다. 위험한 패턴을 우리가
			판정해 막는 쪽은 택하지 않았다: 그 판정은 엔진마다 다르다.
		*/
		return VerdictOf(result, "", contract.CompareExact, 0), result.Stderr
	}

	expected, actual, found := contract.SplitSQLResults(result.Stdout)
	if !found {
		// 하네스가 기대 판정까지 내지 못했다 — 문제의 확인 문자열이 잘못됐다.
		// 사용자 잘못이 아니므로 오답으로 처리하지 않는다.
		return contract.VerdictSystemError, result.Stderr
	}
	if result.ExitCode != 0 {
		// 문법이 틀렸거나 엔진이 거부했다. 무엇이 틀렸는지 보여야 고칠 수 있다.
		return contract.VerdictCompileError, result.Stderr
	}
	// **순서를 무시하지 않는다.** 줄 순서가 곧 어느 문자열의 판정인지를 가리킨다.
	if strings.TrimSpace(expected) != strings.TrimSpace(actual) {
		return contract.VerdictWrongAnswer, ""
	}
	return contract.VerdictAccepted, ""
}

/*
regexFiles 는 하네스가 읽을 파일을 만든다 (#653).

`mode.txt` 를 파일로 두는 이유: 환경변수로 넘기면 샌드박스의 환경 정리 규칙과
얽히고, 하네스가 무엇을 받았는지 실행 뒤에 확인할 수 없다.
*/
func regexFiles(spec *contract.JudgeRegexSpec) map[string]string {
	mode := "search"
	if spec.FullMatch {
		mode = "full"
	}
	if spec.IgnoreCase {
		mode += ":i"
	}
	return map[string]string{
		"cases.txt": spec.Cases,
		"mode.txt":  mode,
	}
}

func regexRuntimeOf(job contract.JudgeJob) string {
	if job.RuntimeID == "" {
		return defaultRegexRuntimeID
	}
	return job.RuntimeID
}
