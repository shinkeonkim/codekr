package judging

import (
	"context"
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

/*
부분 점수 (#473).

**99개를 맞혀도 지금은 그냥 틀린 답이었다.** 배우는 사람은 어디까지 왔는지 모른 채
다시 낸다. 묶음은 대개 제약 조건이고(`N ≤ 1,000`), **묶음 안을 다 맞혀야 그 점수**다.
*/
func partialJob(cases int) contract.JudgeJob {
	testcases := make([]contract.JudgeTestcase, 0, cases)
	for seq := 1; seq <= cases; seq++ {
		// 앞 절반이 1번 묶음, 뒤 절반이 2번 묶음이다.
		group := 1
		if seq > cases/2 {
			group = 2
		}
		testcases = append(testcases, contract.JudgeTestcase{
			Seq: seq, Input: "1", ExpectedOutput: "1\n", GroupNo: group,
		})
	}
	return contract.JudgeJob{
		SubmissionID:  1,
		RuntimeID:     "python:3.13",
		SourceCode:    "print(1)",
		TimeLimitMs:   2000,
		MemoryLimitMb: 256,
		Testcases:     testcases,
		Groups: []contract.JudgeTestcaseGroup{
			{GroupNo: 1, Score: 40},
			{GroupNo: 2, Score: 60},
		},
	}
}

func TestPartialScoreGivesGroupScore(t *testing.T) {
	// 1번 묶음(2개)은 맞고 2번 묶음(2개)의 첫 케이스가 틀린다.
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: "1\n"},
		{Status: contract.StatusOK, Stdout: "1\n"},
		{Status: contract.StatusOK, Stdout: "2\n"},
	}}
	sink := &recordingSink{}

	newTestService(executor, sink).Judge(context.Background(), partialJob(4))

	last := sink.last()
	if last.Score != 40 || last.MaxScore != 100 {
		t.Fatalf("맞힌 묶음의 점수만 받아야 합니다: %d/%d", last.Score, last.MaxScore)
	}
}

// **묶음은 하나만 틀려도 0점이다** (IOI 관례). 케이스마다 주면 묶음의 뜻이 없어진다.
func TestPartialScoreGroupIsAllOrNothing(t *testing.T) {
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: "2\n"},
		{Status: contract.StatusOK, Stdout: "1\n"},
		{Status: contract.StatusOK, Stdout: "1\n"},
		{Status: contract.StatusOK, Stdout: "1\n"},
	}}
	sink := &recordingSink{}

	newTestService(executor, sink).Judge(context.Background(), partialJob(4))

	if last := sink.last(); last.Score != 60 {
		t.Fatalf("1번 묶음은 하나만 틀려도 0점이어야 합니다: %d", last.Score)
	}
}

/*
틀린 묶음의 남은 케이스는 **돌리지 않는다**.

어차피 0점이고, 전부 돌리면 채점 시간이 몇 배가 된다 — 그것은 큐를 쓰는 다른 사람에게도
간다. 다른 묶음은 그대로 돈다.
*/
func TestPartialScoreSkipsRestOfFailedGroup(t *testing.T) {
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: "2\n"},
		{Status: contract.StatusOK, Stdout: "1\n"},
		{Status: contract.StatusOK, Stdout: "1\n"},
	}}
	sink := &recordingSink{}

	newTestService(executor, sink).Judge(context.Background(), partialJob(4))

	// 4개 중 1번 묶음의 둘째는 건너뛰므로 실행은 3번이다.
	if executor.calls != 3 {
		t.Fatalf("틀린 묶음의 남은 케이스를 돌리면 안 됩니다: %d회 실행", executor.calls)
	}
}

// 묶음이 없는 기존 문제는 그대로다 — 점수는 0 이고 판정도 지금과 같다.
func TestPartialScoreLeavesUngroupedProblemsAlone(t *testing.T) {
	executor := &stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK, Stdout: "1\n"}}}
	sink := &recordingSink{}
	job := partialJob(1)
	job.Groups = nil
	job.Testcases[0].GroupNo = 0

	newTestService(executor, sink).Judge(context.Background(), job)

	last := sink.last()
	if last.MaxScore != 0 || last.Verdict != contract.VerdictAccepted {
		t.Fatalf("묶음이 없으면 지금과 같아야 합니다: %+v", last)
	}
}
