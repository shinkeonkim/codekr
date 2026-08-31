package judging

import (
	"context"
	"log/slog"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// 인터랙티브 실행 환경 (#474). 이 값이 없던 시절의 작업은 없다 — 유형과 함께 생겼다.
const defaultInteractiveRuntimeID = "interactive:python3.13"

/*
InteractiveJudge 는 **도는 중에 주고받는** 문제를 채점한다 (#474).

스페셜 저지(#452)는 제출이 끝난 뒤 출력을 채점 코드에 넘긴다. 인터랙티브는 둘이
**동시에 돌며** 서로에게 쓴다 — 그 차이가 전부다. 이 유형이 묻는 것은 답을 아는 것이
아니라 **답에 이르는 길을 설계하는 것**이다.

판정은 채점 코드의 **종료 코드**가 말한다 (#452 의 규약을 넓힌다).

	0  정답
	1  오답
	3  **교착** — 제출이 아무것도 보내지 않고 끝났다. "시간 초과" 와 다른 말을 해야 한다
	그 밖  출제자의 코드가 잘못됐다 — 사용자 잘못이 아니므로 오답으로 처리하지 않는다
*/
type InteractiveJudge struct {
	executor ExecutorClient
	log      *slog.Logger
}

// NewInteractiveJudge 는 인터랙티브 채점기를 만든다.
func NewInteractiveJudge(executor ExecutorClient, log *slog.Logger) *InteractiveJudge {
	return &InteractiveJudge{executor: executor, log: log}
}

// 채점 코드가 쓰는 종료 코드.
const (
	interactorWrongAnswer = 1
	interactorDeadlock    = 3
)

// Judge 는 테스트케이스마다 한 번씩 대화를 붙인다.
//
// 케이스의 입력은 **숨은 값**이다 — 채점 코드가 그것을 읽고 대화를 주관한다.
func (j *InteractiveJudge) Judge(ctx context.Context, job contract.JudgeJob, emit Emitter) Outcome {
	total := len(job.Testcases)
	emit(contract.Event{Type: contract.EventJudging, SubmissionID: job.SubmissionID, TotalCount: total})

	if job.Interactor == "" {
		// 대화를 주관할 코드가 없다. **짐작해 채점하지 않는다** — 그것은 오답이 아니라
		// 출제자의 실수다 (#452 와 같은 판단).
		j.log.Error("인터랙티브 문제인데 채점 코드가 없습니다", "submissionId", job.SubmissionID)
		return Outcome{Summary: Summary{Verdict: contract.VerdictSystemError, TotalCount: total}}
	}

	accumulator := NewAccumulator(total).WithGroups(job.Groups)
	for _, testcase := range job.Testcases {
		if accumulator.GroupFailed(testcase.GroupNo) {
			accumulator.AddInGroup(testcase.GroupNo, contract.VerdictWrongAnswer, 0, 0)
			continue
		}
		result, err := j.executor.Run(ctx, contract.ExecJob{
			RuntimeID:     j.runtimeOf(job),
			SourceCode:    job.SourceCode,
			TimeLimitMs:   job.TimeLimitMs,
			MemoryLimitMb: job.MemoryLimitMb,
			ExtraFiles: map[string]string{
				"interactor.py": job.Interactor,
				// 숨은 값. 케이스의 입력이 그대로 온다.
				"case.txt": testcase.Input,
			},
		})
		if err != nil {
			j.log.Error("실행 요청 실패", "submissionId", job.SubmissionID, "error", err)
			result = executorUnreachable()
		}

		verdict, detail := j.verdictOf(result)
		accumulator.AddInGroup(testcase.GroupNo, verdict, result.RuntimeMs, result.MemoryKb)
		emit(contract.Event{
			Type:          contract.EventTestcase,
			SubmissionID:  job.SubmissionID,
			Seq:           testcase.Seq,
			Verdict:       verdict,
			RuntimeMs:     result.RuntimeMs,
			MemoryKb:      result.MemoryKb,
			StderrExcerpt: excerpt(detail),
		})
	}
	return Outcome{Summary: accumulator.Summarize()}
}

func (j *InteractiveJudge) verdictOf(result contract.ExecResult) (verdict contract.Verdict, detail string) {
	// 시간·메모리·인프라 실패의 뜻은 유형과 무관하다. 그대로 읽는다.
	if result.Status != contract.StatusOK && result.Status != contract.StatusRuntimeError {
		return VerdictOf(result, "", contract.CompareExact, 0), result.Stderr
	}

	switch result.ExitCode {
	case 0:
		return contract.VerdictAccepted, ""
	case interactorWrongAnswer:
		return contract.VerdictWrongAnswer, result.Stderr
	case interactorDeadlock:
		/*
			**교착과 시간 초과를 가른다.**

			제출이 아무것도 보내지 않고 끝났다 — 이 유형에서 가장 흔한 원인은
			**출력을 flush 하지 않은 것**이고, 사용자 잘못이지만 사용자가 알기 어렵다.
			"시간 초과" 로 보이면 엉뚱한 곳(알고리즘)을 고치게 된다.
		*/
		return contract.VerdictWrongAnswer, "채점 코드가 입력을 기다리다 끝났습니다. 출력을 flush 했는지 확인하세요."
	default:
		// 출제자의 코드가 죽었다. **사용자에게 보이지 않는다** — 정답이 새어 나갈 수 있다.
		j.log.Error("채점 코드가 알 수 없는 코드로 끝났습니다", "exitCode", result.ExitCode, "stderr", result.Stderr)
		return contract.VerdictSystemError, ""
	}
}

func (j *InteractiveJudge) runtimeOf(job contract.JudgeJob) string {
	if job.RuntimeID == "" {
		return defaultInteractiveRuntimeID
	}
	return job.RuntimeID
}
