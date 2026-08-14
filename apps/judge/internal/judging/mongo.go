package judging

import (
	"context"
	"log/slog"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// MongoDB 실행 환경 (#527). 이 값이 없던 시절의 작업은 없다 — 유형과 함께 생겼다.
const defaultMongoRuntimeID = "mongodb:7"

/*
MongoJudge 는 MongoDB 문제를 채점한다 (#527).

**Redis 와 채점 모델이 같다.** 시드로 시작 상태를 만들고, 정답 스크립트를 돌린 쪽과
제출을 돌린 쪽에서 **같은 확인 스크립트**를 돌려 그 출력을 견준다.

그런데도 유형과 채점기를 나눈 이유는 **질의 언어가 다르기 때문**이다. #454 가 SQL 에
MariaDB 를 더할 때는 런타임만 얹으면 됐다 — 두 제품이 같은 언어를 쓴다. Redis 와
MongoDB 는 그렇지 않아 스펙 표부터 갈린다.

바꾸지 않은 것: 하네스의 출력 형식과 비교 함수다. 어느 제품이든 `--- codekr:expected` /
`--- codekr:actual` 로 나뉜 줄을 내므로, **채점기는 무엇이 돌았는지 몰라도 된다.**
*/
type MongoJudge struct {
	executor ExecutorClient
	log      *slog.Logger
}

// NewMongoJudge 는 MongoDB 채점기를 만든다.
func NewMongoJudge(executor ExecutorClient, log *slog.Logger) *MongoJudge {
	return &MongoJudge{executor: executor, log: log}
}

// Judge 는 제출 스크립트를 한 번 실행하고 끝난 뒤의 상태를 정답과 비교한다.
func (j *MongoJudge) Judge(ctx context.Context, job contract.JudgeJob, emit Emitter) Outcome {
	emit(contract.Event{Type: contract.EventJudging, SubmissionID: job.SubmissionID, TotalCount: 1})

	if job.Mongo == nil {
		// 스펙 없이 MongoDB 문제가 큐에 들어왔다. 짐작해 채점하지 않는다.
		j.log.Error("MongoDB 문제인데 스펙이 없습니다", "submissionId", job.SubmissionID)
		return Outcome{Summary: Summary{Verdict: contract.VerdictSystemError, TotalCount: 1}}
	}

	result, err := j.executor.Run(ctx, contract.ExecJob{
		RuntimeID:     mongoRuntimeOf(job),
		SourceCode:    job.SourceCode,
		TimeLimitMs:   job.TimeLimitMs,
		MemoryLimitMb: job.MemoryLimitMb,
		ExtraFiles:    mongoFiles(job.Mongo),
	})
	if err != nil {
		j.log.Error("실행 요청 실패", "submissionId", job.SubmissionID, "error", err)
		result = contract.ExecResult{Status: contract.StatusSystemError, Stderr: err.Error()}
	}

	verdict, detail := j.verdictOf(result, job.Mongo.IgnoreOrder)
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
	// 스크립트 자체가 잘못된 것은 컴파일 오류로 대응한다 — SQL·Redis 와 같은 판단이다.
	if verdict == contract.VerdictCompileError {
		return Outcome{Summary: summary, CompileError: excerpt(detail)}
	}
	return Outcome{Summary: summary}
}

func (j *MongoJudge) verdictOf(
	result contract.ExecResult,
	ignoreOrder bool,
) (verdict contract.Verdict, detail string) {
	if result.Status != contract.StatusOK && result.Status != contract.StatusRuntimeError {
		return VerdictOf(result, "", contract.CompareExact, 0), result.Stderr
	}

	expected, actual, found := contract.SplitSQLResults(result.Stdout)
	if !found {
		// 하네스가 기대 상태까지 내지 못했다 — 시드나 정답 스크립트가 잘못됐다.
		// 사용자 잘못이 아니므로 오답으로 처리하지 않는다.
		return contract.VerdictSystemError, result.Stderr
	}
	if result.ExitCode != 0 {
		// 제출 스크립트가 막혔거나 잘못됐다. 어느 쪽이든 사용자에게 보일 것은 stderr 다.
		return contract.VerdictCompileError, result.Stderr
	}
	if contract.NormalizeSQLRows(expected, ignoreOrder) != contract.NormalizeSQLRows(actual, ignoreOrder) {
		return contract.VerdictWrongAnswer, ""
	}
	return contract.VerdictAccepted, ""
}

// mongoFiles 는 하네스가 읽을 파일을 만든다 (#527). 하네스가 제품마다 하나씩이므로
// 확장자도 제품의 것을 쓴다.
func mongoFiles(spec *contract.JudgeMongoSpec) map[string]string {
	files := map[string]string{
		"answer.mongo": spec.Answer,
		"verify.mongo": spec.Verify,
	}
	if spec.Seed != "" {
		files["seed.mongo"] = spec.Seed
	}
	return files
}

func mongoRuntimeOf(job contract.JudgeJob) string {
	if job.RuntimeID == "" {
		return defaultMongoRuntimeID
	}
	return job.RuntimeID
}
