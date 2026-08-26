package judging

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

/*
MongoDB 채점기 시험 (#656).

**여섯 가지 채점 방식 중 이것만 시험이 없었다.** `stdio`·`sql`·`redis`·`function`·
`interactive` 는 있고 여기만 비어 있었다 — 유형이 마지막(#527)에 들어오면서 시험이
함께 오지 않았다. #642 로 커버리지를 재고 나서야 보였다.

**라이브 샌드박스 시험(`TestLiveMongo`)이 있는 것과 다른 이야기다.** 그쪽은 컨테이너가
도는지를 보고 30분짜리 잡에서만 돌지만, 여기서 묻는 것은 **하네스가 낸 것을 판정으로
바꾸는 규칙**이다.
*/

func mongoJob(script string, ignoreOrder bool) contract.JudgeJob {
	return contract.JudgeJob{
		SubmissionID:  11,
		Kind:          contract.KindJudgeMongo,
		RuntimeID:     defaultMongoRuntimeID,
		SourceCode:    script,
		TimeLimitMs:   10000,
		MemoryLimitMb: 512,
		Mongo: &contract.JudgeMongoSpec{
			Seed:        `db.orders.insertOne({ id: 1, total: 100 })`,
			Answer:      `db.orders.updateOne({ id: 1 }, { $set: { total: 150 } })`,
			Verify:      `db.orders.find({}, { _id: 0 }).sort({ id: 1 }).forEach(printjson)`,
			IgnoreOrder: ignoreOrder,
		},
	}
}

func TestMongoJudgeAcceptsMatchingState(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("1\t150", "1\t150")},
	}}

	newTestService(executor, sink).Judge(context.Background(), mongoJob("db.orders.updateOne(…)", false))

	if last := sink.last(); last.Verdict != contract.VerdictAccepted {
		t.Fatalf("정답이어야 합니다: %s", last.Verdict)
	}
}

// **남는 것은 결과가 아니라 상태다** — Redis 와 같은 모델이다.
func TestMongoJudgeRejectsDifferentState(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("1\t150", "1\t100")},
	}}

	newTestService(executor, sink).Judge(context.Background(), mongoJob("db.orders.find({})", false))

	if last := sink.last(); last.Verdict != contract.VerdictWrongAnswer {
		t.Fatalf("오답이어야 합니다: %s", last.Verdict)
	}
}

func TestMongoJudgeKeepsOrderByDefault(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("a\nb", "b\na")},
	}}

	newTestService(executor, sink).Judge(context.Background(), mongoJob("…", false))

	if last := sink.last(); last.Verdict != contract.VerdictWrongAnswer {
		t.Fatalf("순서가 다르면 오답이어야 합니다: %s", last.Verdict)
	}
}

func TestMongoJudgeIgnoresOrderWhenAsked(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("a\nb", "b\na")},
	}}

	newTestService(executor, sink).Judge(context.Background(), mongoJob("…", true))

	if last := sink.last(); last.Verdict != contract.VerdictAccepted {
		t.Fatalf("순서를 무시하기로 했으면 정답이어야 합니다: %s", last.Verdict)
	}
}

// 스크립트가 잘못된 것은 오답이 아니라 **무엇이 잘못됐는지 보여야 한다.**
func TestMongoJudgeReportsBrokenScriptAsCompileError(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{
			Status:   contract.StatusRuntimeError,
			ExitCode: 1,
			Stdout:   harnessOutput("1\t150", ""),
			Stderr:   "SyntaxError: unexpected token",
		},
	}}

	newTestService(executor, sink).Judge(context.Background(), mongoJob("db.orders.updateOne(", false))

	if last := sink.last(); last.Verdict != contract.VerdictCompileError {
		t.Fatalf("잘못된 스크립트는 컴파일 오류로 대응한다: %s", last.Verdict)
	}
}

/*
하네스가 기대 상태를 못 냈으면 **사용자 잘못이 아니다.**

시드나 정답 스크립트가 잘못된 것이라 오답으로 처리하면 **출제자의 실수가 푸는 사람의
기록에 남는다.** 그래서 시스템 오류다.
*/
func TestMongoJudgeTreatsMissingExpectedAsSystemError(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: "구분선 없는 출력", Stderr: "seed 가 실패했습니다"},
	}}

	newTestService(executor, sink).Judge(context.Background(), mongoJob("…", false))

	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("기대 상태가 없으면 시스템 오류여야 합니다: %s", last.Verdict)
	}
}

// 실행 자체가 한도에 걸린 것은 하네스 출력을 볼 것도 없다.
func TestMongoJudgeMapsExecutionLimits(t *testing.T) {
	for _, each := range []struct {
		status contract.ExecStatus
		want   contract.Verdict
	}{
		{contract.StatusTimeLimitExceeded, contract.VerdictTimeLimitExceeded},
		{contract.StatusMemoryLimitExceeded, contract.VerdictMemoryLimitExceeded},
		{contract.StatusOutputLimitExceeded, contract.VerdictOutputLimitExceeded},
	} {
		sink := &recordingSink{}
		executor := &stubExecutor{results: []contract.ExecResult{{Status: each.status}}}

		newTestService(executor, sink).Judge(context.Background(), mongoJob("…", false))

		if last := sink.last(); last.Verdict != each.want {
			t.Fatalf("%s 는 %s 여야 합니다: %s", each.status, each.want, last.Verdict)
		}
	}
}

// 실행기에 닿지 못한 것도 사용자 잘못이 아니다.
func TestMongoJudgeTreatsExecutorFailureAsSystemError(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{err: errors.New("실행기가 응답하지 않습니다")}

	newTestService(executor, sink).Judge(context.Background(), mongoJob("…", false))

	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류여야 합니다: %s", last.Verdict)
	}
}

func TestMongoJudgeSendsSpecFilesToExecutor(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("1", "1"),
	}}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))

	NewMongoJudge(captured, log).Judge(context.Background(), mongoJob("…", false), func(contract.Event) {})

	for _, name := range []string{"answer.mongo", "verify.mongo", "seed.mongo"} {
		if captured.job.ExtraFiles[name] == "" {
			t.Fatalf("%s 를 실어야 합니다: %+v", name, captured.job.ExtraFiles)
		}
	}
}

// 시드는 없어도 된다 — 빈 시드를 파일로 실으면 하네스가 빈 스크립트를 돌린다.
func TestMongoJudgeOmitsSeedFileWhenAbsent(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("1", "1"),
	}}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	job := mongoJob("…", false)
	job.Mongo.Seed = ""

	NewMongoJudge(captured, log).Judge(context.Background(), job, func(contract.Event) {})

	if _, found := captured.job.ExtraFiles["seed.mongo"]; found {
		t.Fatalf("시드가 없으면 파일도 없어야 합니다: %+v", captured.job.ExtraFiles)
	}
}

/*
런타임을 안 정한 문제는 **기본값으로 간다.**

이 갈래가 없으면 빈 런타임 이름이 실행기로 가고, 거기서 나는 오류는
"MongoDB 문제인데 런타임이 없다" 로 읽히지 않는다.
*/
func TestMongoJudgeFallsBackToDefaultRuntime(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("1", "1"),
	}}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	job := mongoJob("…", false)
	job.RuntimeID = ""

	NewMongoJudge(captured, log).Judge(context.Background(), job, func(contract.Event) {})

	if captured.job.RuntimeID != defaultMongoRuntimeID {
		t.Fatalf("기본 런타임으로 보내야 합니다: %q", captured.job.RuntimeID)
	}
}

// 스펙 없이 들어온 작업을 짐작해 채점하지 않는다 — 그것은 출제자의 실수다.
func TestMongoJudgeRefusesJobWithoutSpec(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK}}}
	job := mongoJob("…", false)
	job.Mongo = nil

	newTestService(executor, sink).Judge(context.Background(), job)

	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류여야 합니다: %s", last.Verdict)
	}
	if executor.calls != 0 {
		t.Fatalf("실행기를 부르지 않아야 합니다: %d회", executor.calls)
	}
}
