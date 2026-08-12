package judging

import (
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

func TestVerdictOfComparesOutputOnlyWhenExecutionSucceeded(t *testing.T) {
	cases := []struct {
		status   contract.ExecStatus
		stdout   string
		expected contract.Verdict
		name     string
	}{
		{contract.StatusOK, "3\n", contract.VerdictAccepted, "정답"},
		{contract.StatusOK, "4\n", contract.VerdictWrongAnswer, "오답"},
		{contract.StatusTimeLimitExceeded, "", contract.VerdictTimeLimitExceeded, "시간 초과"},
		{contract.StatusMemoryLimitExceeded, "", contract.VerdictMemoryLimitExceeded, "메모리 초과"},
		{contract.StatusCompileError, "", contract.VerdictCompileError, "컴파일 오류"},
		{contract.StatusRuntimeError, "", contract.VerdictRuntimeError, "런타임 오류"},
		{contract.StatusOutputLimitExceeded, "", contract.VerdictOutputLimitExceeded, "출력 초과"},
		{contract.StatusSystemError, "", contract.VerdictSystemError, "인프라 오류"},
	}

	for _, c := range cases {
		result := contract.ExecResult{Status: c.status, Stdout: c.stdout}
		if got := VerdictOf(result, "3\n", contract.CompareExact, 0); got != c.expected {
			t.Errorf("%s: VerdictOf = %s, 기대값 %s", c.name, got, c.expected)
		}
	}
}

func TestAccumulatorReportsFirstFailureAsFinalVerdict(t *testing.T) {
	accumulator := NewAccumulator(3)
	accumulator.Add(contract.VerdictAccepted, 10, 1000)
	accumulator.Add(contract.VerdictWrongAnswer, 20, 2000)
	accumulator.Add(contract.VerdictTimeLimitExceeded, 2000, 1500)

	summary := accumulator.Summarize()

	if summary.Verdict != contract.VerdictWrongAnswer {
		t.Errorf("최종 판정은 첫 실패여야 합니다: %s", summary.Verdict)
	}
	if summary.PassedCount != 1 || summary.TotalCount != 3 {
		t.Errorf("통과 수 집계가 잘못되었습니다: %+v", summary)
	}
	if summary.MaxRuntimeMs != 2000 || summary.MaxMemoryKb != 2000 {
		t.Errorf("최댓값 집계가 잘못되었습니다: %+v", summary)
	}
}

func TestAccumulatorReturnsAcceptedWhenAllPass(t *testing.T) {
	accumulator := NewAccumulator(2)
	accumulator.Add(contract.VerdictAccepted, 10, 100)
	accumulator.Add(contract.VerdictAccepted, 12, 120)

	if summary := accumulator.Summarize(); summary.Verdict != contract.VerdictAccepted || summary.PassedCount != 2 {
		t.Errorf("전부 통과 시 ACCEPTED 여야 합니다: %+v", summary)
	}
}
