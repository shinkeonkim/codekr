package judging

import (
	"context"
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// harnessExecutor 는 실행기에 **무엇이 갔는지**를 본다 (여러 번 부르므로 전부 모은다).
type harnessExecutor struct {
	jobs   []contract.ExecJob
	result contract.ExecResult
}

func (c *harnessExecutor) Run(_ context.Context, job contract.ExecJob) (contract.ExecResult, error) {
	c.jobs = append(c.jobs, job)
	return c.result, nil
}

/*
함수형 문제 (#447, #421).

**채점 방식은 그대로다** — 하네스가 입력을 읽고 결과를 찍으므로 stdout 을 비교하는 것은
같다. 다른 것은 하네스가 함께 간다는 것뿐이다.
*/
func TestFunctionJobCarriesHarnessToExecutor(t *testing.T) {
	executor := &harnessExecutor{result: contract.ExecResult{Status: contract.StatusOK, Stdout: "3\n"}}
	job := newJob(2)
	job.Kind = contract.KindJudgeFunction
	job.Harness = "from solution import solve"

	newTestService(executor, &recordingSink{}).Judge(context.Background(), job)

	if len(executor.jobs) != 2 {
		t.Fatalf("테스트케이스마다 한 번씩 돌아야 합니다: %d", len(executor.jobs))
	}
	for _, sent := range executor.jobs {
		if sent.HarnessSource != job.Harness {
			t.Errorf("하네스가 실행기까지 가지 않았습니다: %q", sent.HarnessSource)
		}
		if sent.SourceCode != job.SourceCode {
			t.Errorf("사용자 코드가 바뀌었습니다: %q", sent.SourceCode)
		}
	}
}

func TestFunctionJobWithoutHarnessIsNotJudged(t *testing.T) {
	/*
		**짐작해 돌리지 않는다.** 하네스 없이 함수만 있는 코드를 실행하면 출력이 없고,
		그러면 "틀렸다" 로 기록된다 — 사용자 잘못이 아닌데 사용자 기록에 남는다.
	*/
	executor := &harnessExecutor{result: contract.ExecResult{Status: contract.StatusOK}}
	sink := &recordingSink{}
	job := newJob(3)
	job.Kind = contract.KindJudgeFunction

	newTestService(executor, sink).Judge(context.Background(), job)

	if len(executor.jobs) != 0 {
		t.Errorf("실행기를 부르지 말아야 합니다: %d회", len(executor.jobs))
	}
	if verdict := sink.last().Verdict; verdict != contract.VerdictSystemError {
		t.Errorf("우리 잘못이므로 SYSTEM_ERROR 여야 합니다: %s", verdict)
	}
}

func TestStdioJobStillSendsNoHarness(t *testing.T) {
	// 지금 문제들이 그대로 돌아야 한다 — 하네스는 함수형에만 실린다.
	executor := &harnessExecutor{result: contract.ExecResult{Status: contract.StatusOK, Stdout: "3\n"}}

	newTestService(executor, &recordingSink{}).Judge(context.Background(), newJob(1))

	if executor.jobs[0].HarnessSource != "" {
		t.Errorf("stdio 작업에 하네스가 실렸습니다: %q", executor.jobs[0].HarnessSource)
	}
}
