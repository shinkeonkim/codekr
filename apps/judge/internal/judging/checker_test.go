package judging

import (
	"context"
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// checkerExecutor 는 사용자 프로그램과 채점 코드에 각각 다른 결과를 준다.
type checkerExecutor struct {
	program contract.ExecResult
	checker contract.ExecResult
	jobs    []contract.ExecJob
}

func (c *checkerExecutor) Run(_ context.Context, job contract.ExecJob) (contract.ExecResult, error) {
	c.jobs = append(c.jobs, job)
	if job.SourceCode == "CHECKER CODE" {
		return c.checker, nil
	}
	return c.program, nil
}

func checkerJob() contract.JudgeJob {
	job := newJob(1)
	job.Comparison = contract.CompareChecker
	job.Checker = "CHECKER CODE"
	return job
}

/*
스페셜 저지 (#452).

**정답이 여럿인 문제는 기대값과 견줄 수 없다.** 그때는 견주는 대신 물어본다.
*/
func TestCheckerAcceptsWhateverItSays(t *testing.T) {
	executor := &checkerExecutor{
		program: contract.ExecResult{Status: contract.StatusOK, Stdout: "아무 답이나\n"},
		checker: contract.ExecResult{Status: contract.StatusOK, ExitCode: 0},
	}
	sink := &recordingSink{}

	newTestService(executor, sink).Judge(context.Background(), checkerJob())

	if verdict := sink.last().Verdict; verdict != contract.VerdictAccepted {
		t.Errorf("채점 코드가 맞다고 했는데 %s 다", verdict)
	}
	// 채점 코드에 무엇이 갔는지 본다 — 입력·제출 출력·정답 출력 셋이다.
	files := executor.jobs[1].ExtraFiles
	if files[checkerOutputFile] != "아무 답이나\n" {
		t.Errorf("제출한 출력이 안 갔다: %q", files[checkerOutputFile])
	}
	if files[checkerInputFile] == "" || files[checkerExpectedFile] == "" {
		t.Errorf("입력·정답이 안 갔다: %v", files)
	}
}

func TestCheckerRejectsWithExitCodeOne(t *testing.T) {
	// **약속된 오답이다** — 죽은 것이 아니라 "틀렸다" 로 끝낸 것이다.
	executor := &checkerExecutor{
		program: contract.ExecResult{Status: contract.StatusOK, Stdout: "틀린 답\n"},
		checker: contract.ExecResult{Status: contract.StatusRuntimeError, ExitCode: 1},
	}
	sink := &recordingSink{}

	newTestService(executor, sink).Judge(context.Background(), checkerJob())

	if verdict := sink.last().Verdict; verdict != contract.VerdictWrongAnswer {
		t.Errorf("채점 코드가 틀렸다고 했는데 %s 다", verdict)
	}
}

func TestBrokenCheckerIsNotTheUsersFault(t *testing.T) {
	/*
		**출제자의 실수를 사용자 기록에 남기지 않는다** — #60 이 SQL 에서 한 판단과 같다.
		문법 오류·무한 루프·약속하지 않은 종료 코드가 전부 여기로 온다.
	*/
	for _, broken := range []contract.ExecResult{
		{Status: contract.StatusRuntimeError, ExitCode: 2},
		{Status: contract.StatusTimeLimitExceeded},
		{Status: contract.StatusSystemError},
		{Status: contract.StatusMemoryLimitExceeded},
	} {
		executor := &checkerExecutor{
			program: contract.ExecResult{Status: contract.StatusOK, Stdout: "답\n"},
			checker: broken,
		}
		sink := &recordingSink{}

		newTestService(executor, sink).Judge(context.Background(), checkerJob())

		if verdict := sink.last().Verdict; verdict != contract.VerdictSystemError {
			t.Errorf("%v 로 끝난 채점 코드가 %s 를 냈다 — 오답이 아니어야 한다", broken.Status, verdict)
		}
	}
}

func TestCheckerIsNotAskedWhenProgramFailed(t *testing.T) {
	// 죽은 프로그램의 출력을 물어볼 이유가 없고, TLE 를 "틀렸다" 로 바꿀 이유도 없다.
	executor := &checkerExecutor{
		program: contract.ExecResult{Status: contract.StatusTimeLimitExceeded},
		checker: contract.ExecResult{Status: contract.StatusOK, ExitCode: 0},
	}
	sink := &recordingSink{}

	newTestService(executor, sink).Judge(context.Background(), checkerJob())

	if verdict := sink.last().Verdict; verdict != contract.VerdictTimeLimitExceeded {
		t.Errorf("시간 초과가 %s 로 바뀌었다", verdict)
	}
	if len(executor.jobs) != 1 {
		t.Errorf("채점 코드를 부르지 말아야 한다: %d회", len(executor.jobs)-1)
	}
}

func TestMissingCheckerIsSystemError(t *testing.T) {
	// 견줄 기대값도 없고 물어볼 코드도 없다. 이것도 출제자의 실수다.
	job := checkerJob()
	job.Checker = ""
	executor := &checkerExecutor{program: contract.ExecResult{Status: contract.StatusOK, Stdout: "답\n"}}
	sink := &recordingSink{}

	newTestService(executor, sink).Judge(context.Background(), job)

	if verdict := sink.last().Verdict; verdict != contract.VerdictSystemError {
		t.Errorf("채점 코드가 없는데 %s 다", verdict)
	}
}

func TestExactComparisonIsUntouched(t *testing.T) {
	// 지금 문제들이 그대로 돌아야 한다.
	executor := &checkerExecutor{program: contract.ExecResult{Status: contract.StatusOK, Stdout: "3\n"}}
	sink := &recordingSink{}

	newTestService(executor, sink).Judge(context.Background(), newJob(1))

	if verdict := sink.last().Verdict; verdict != contract.VerdictAccepted {
		t.Errorf("정확 일치 채점이 바뀌었다: %s", verdict)
	}
	if len(executor.jobs) != 1 {
		t.Errorf("채점 코드를 부르면 안 된다: %d회", len(executor.jobs)-1)
	}
}
