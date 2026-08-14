package judging

import (
	"context"
	"log/slog"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// Redis 실행 환경 (#455). 이 값이 없던 시절의 작업은 없다 — 유형과 함께 생겼다.
const defaultRedisRuntimeID = "redis:7"

/*
RedisJudge 는 Redis 문제를 채점한다 (#455).

**SQL 과 채점 모델이 다르다.** 제출이 명령의 연속이라 남는 것은 결과가 아니라 상태다.
그래서 정답 명령을 돌린 인스턴스와 제출을 돌린 인스턴스에서 **같은 확인 명령**을 돌려
그 출력을 견준다.

바꾸지 않은 것: 하네스의 출력 형식과 비교 함수다. 어느 제품이든 `--- codekr:expected` /
`--- codekr:actual` 로 나뉜 줄을 내므로, **채점기는 무엇이 돌았는지 몰라도 된다.**
*/
type RedisJudge struct {
	executor ExecutorClient
	log      *slog.Logger
}

// NewRedisJudge 는 Redis 채점기를 만든다.
func NewRedisJudge(executor ExecutorClient, log *slog.Logger) *RedisJudge {
	return &RedisJudge{executor: executor, log: log}
}

// Judge 는 제출 명령을 한 번 실행하고 끝난 뒤의 상태를 정답과 비교한다.
func (j *RedisJudge) Judge(ctx context.Context, job contract.JudgeJob, emit Emitter) Outcome {
	emit(contract.Event{Type: contract.EventJudging, SubmissionID: job.SubmissionID, TotalCount: 1})

	if job.Redis == nil {
		// 스펙 없이 Redis 문제가 큐에 들어왔다. 짐작해 채점하지 않는다.
		j.log.Error("Redis 문제인데 스펙이 없습니다", "submissionId", job.SubmissionID)
		return Outcome{Summary: Summary{Verdict: contract.VerdictSystemError, TotalCount: 1}}
	}

	result, err := j.executor.Run(ctx, contract.ExecJob{
		RuntimeID:     noSQLRuntimeOf(job),
		SourceCode:    job.SourceCode,
		TimeLimitMs:   job.TimeLimitMs,
		MemoryLimitMb: job.MemoryLimitMb,
		ExtraFiles:    redisFiles(job.Redis),
	})
	if err != nil {
		j.log.Error("실행 요청 실패", "submissionId", job.SubmissionID, "error", err)
		result = contract.ExecResult{Status: contract.StatusSystemError, Stderr: err.Error()}
	}

	verdict, detail := j.verdictOf(result, job.Redis.IgnoreOrder)
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
	// 명령 자체가 잘못된 것은 컴파일 오류로 대응한다 — SQL 과 같은 판단이다.
	if verdict == contract.VerdictCompileError {
		return Outcome{Summary: summary, CompileError: excerpt(detail)}
	}
	return Outcome{Summary: summary}
}

func (j *RedisJudge) verdictOf(
	result contract.ExecResult,
	ignoreOrder bool,
) (verdict contract.Verdict, detail string) {
	if result.Status != contract.StatusOK && result.Status != contract.StatusRuntimeError {
		return VerdictOf(result, "", contract.CompareExact, 0), result.Stderr
	}

	expected, actual, found := contract.SplitSQLResults(result.Stdout)
	if !found {
		// 하네스가 기대 상태까지 내지 못했다 — 시드나 정답 명령이 잘못됐다.
		// 사용자 잘못이 아니므로 오답으로 처리하지 않는다.
		return contract.VerdictSystemError, result.Stderr
	}
	if result.ExitCode != 0 {
		// 제출 명령이 막혔거나 잘못됐다. 어느 쪽이든 사용자에게 보일 것은 stderr 다.
		return contract.VerdictCompileError, result.Stderr
	}
	if contract.NormalizeSQLRows(expected, ignoreOrder) != contract.NormalizeSQLRows(actual, ignoreOrder) {
		return contract.VerdictWrongAnswer, ""
	}
	return contract.VerdictAccepted, ""
}

/*
redisFiles 는 하네스가 읽을 파일을 만든다 (#455).

이름이 `.redis` 인 것은 지금 제품이 Redis 하나이기 때문이 아니라, **하네스가 제품마다
하나씩**이기 때문이다 — MongoDB 를 얹으면 그쪽 하네스가 자기 이름으로 읽는다.
*/
func redisFiles(spec *contract.JudgeRedisSpec) map[string]string {
	files := map[string]string{
		"answer.redis": spec.Answer,
		"verify.redis": spec.Verify,
	}
	if spec.Seed != "" {
		files["seed.redis"] = spec.Seed
	}
	return files
}

func noSQLRuntimeOf(job contract.JudgeJob) string {
	if job.RuntimeID == "" {
		return defaultRedisRuntimeID
	}
	return job.RuntimeID
}
