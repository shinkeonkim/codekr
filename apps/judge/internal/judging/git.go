package judging

import (
	"context"
	"log/slog"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// Git 실행 환경 (#654). 새 이미지를 만들지 않고 C/C++ 이 쓰는 것을 함께 쓴다.
const defaultGitRuntimeID = "git:2"

/*
GitJudge 는 Git 문제를 채점한다 (#654).

**Redis(#455)와 채점 모델이 같다** — 제출이 명령의 연속이고 남는 것은 상태다.
정답 명령을 돌린 저장소와 제출을 돌린 저장소에서 **같은 확인 명령**을 돌려 견준다.

**순서를 무시하지 않는다.** 확인 명령이 대개 `git log` 이고, 거기서 줄 순서는
곧 커밋 순서다 — 무시하면 순서가 뒤집힌 히스토리가 정답이 된다.
*/
type GitJudge struct {
	executor ExecutorClient
	log      *slog.Logger
}

// NewGitJudge 는 Git 채점기를 만든다.
func NewGitJudge(executor ExecutorClient, log *slog.Logger) *GitJudge {
	return &GitJudge{executor: executor, log: log}
}

// Judge 는 제출 명령을 시드 위에 돌리고 끝난 뒤의 상태를 정답과 비교한다.
func (j *GitJudge) Judge(ctx context.Context, job contract.JudgeJob, emit Emitter) Outcome {
	emit(contract.Event{Type: contract.EventJudging, SubmissionID: job.SubmissionID, TotalCount: 1})

	if job.Git == nil {
		// 스펙 없이 Git 문제가 큐에 들어왔다. 짐작해 채점하지 않는다.
		j.log.Error("Git 문제인데 스펙이 없습니다", "submissionId", job.SubmissionID)
		return Outcome{Summary: Summary{Verdict: contract.VerdictSystemError, TotalCount: 1}}
	}

	result, err := j.executor.Run(ctx, contract.ExecJob{
		RuntimeID:     gitRuntimeOf(job),
		SourceCode:    job.SourceCode,
		TimeLimitMs:   job.TimeLimitMs,
		MemoryLimitMb: job.MemoryLimitMb,
		ExtraFiles:    gitFiles(job.Git),
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
	// 막히거나 실패한 명령은 컴파일 오류로 대응한다 — Redis 와 같은 판단이다.
	if verdict == contract.VerdictCompileError {
		return Outcome{Summary: summary, CompileError: excerpt(detail)}
	}
	return Outcome{Summary: summary}
}

func (j *GitJudge) verdictOf(result contract.ExecResult) (verdict contract.Verdict, detail string) {
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
		/*
			제출이 막혔거나 실패했다.

			**어느 쪽이든 사용자에게 보일 것은 stderr 다** — 하네스가 거기에
			"git 명령만 쓸 수 있습니다" 나 git 자신의 오류를 담아 준다.
			네트워크 명령은 여기로 온다: `transport 'https' not allowed`.
		*/
		return contract.VerdictCompileError, result.Stderr
	}
	// **순서를 무시하지 않는다.** `git log` 의 줄 순서가 곧 커밋 순서다.
	if contract.NormalizeSQLRows(expected, false) != contract.NormalizeSQLRows(actual, false) {
		return contract.VerdictWrongAnswer, ""
	}
	return contract.VerdictAccepted, ""
}

// gitFiles 는 하네스가 읽을 파일을 만든다 (#654). 확장자는 다른 하네스와 같은 결이다.
func gitFiles(spec *contract.JudgeGitSpec) map[string]string {
	files := map[string]string{
		"answer.git": spec.Answer,
		"verify.git": spec.Verify,
	}
	if spec.Seed != "" {
		files["seed.git"] = spec.Seed
	}
	return files
}

func gitRuntimeOf(job contract.JudgeJob) string {
	if job.RuntimeID == "" {
		return defaultGitRuntimeID
	}
	return job.RuntimeID
}
