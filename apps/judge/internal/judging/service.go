package judging

import (
	"context"
	"log/slog"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// stderr 발췌 길이. 화면에 힌트를 주기에 충분하고 이벤트를 부풀리지 않는 정도.
const stderrExcerptLimit = 512

// ExecutorClient 는 실행 작업 하나를 결과로 바꿔 주는 대상이다 (실제 구현은 큐 디스패처).
type ExecutorClient interface {
	Run(ctx context.Context, job contract.ExecJob) (contract.ExecResult, error)
}

// EventSink 는 진행 이벤트를 밖으로 흘려보내는 대상이다.
type EventSink interface {
	Publish(ctx context.Context, event contract.Event) error
}

// Emitter 는 채점 도중의 진행 이벤트를 내보낸다. 유형 구현이 발행 경로를 알 필요가 없게 한다.
type Emitter func(contract.Event)

// Outcome 은 유형 구현이 돌려주는 채점 결과다. 완료 이벤트는 Service 가 만든다 —
// 유형마다 완료 이벤트 모양이 달라지면 api 쪽 처리가 유형을 알아야 한다.
type Outcome struct {
	Summary      Summary
	CompileError string
}

// Kind 는 문제 유형 하나의 채점 방식이다 (#59).
//
// **새 유형을 추가할 때 손대는 곳은 여기 구현 하나와 아래 kinds 맵뿐이다.**
// 진행/완료 이벤트, 제약 검증, 실패 처리는 Service 가 공통으로 맡는다.
type Kind interface {
	Judge(ctx context.Context, job contract.JudgeJob, emit Emitter) Outcome
}

// Service 는 작업을 유형에 맞는 채점기로 넘기고 공통 처리를 맡는다.
type Service struct {
	kinds  map[string]Kind
	events EventSink
	log    *slog.Logger
}

// NewService 는 채점 서비스를 만든다.
func NewService(executor ExecutorClient, events EventSink, log *slog.Logger) *Service {
	return &Service{
		kinds: map[string]Kind{
			contract.KindJudgeStdio: NewStdioJudge(executor, log),
			// **함수형도 stdout 을 비교한다** (#447). 다른 것은 하네스가 입출력을
			// 맡는다는 것뿐이라, 채점 방식을 새로 만들지 않는다.
			contract.KindJudgeFunction: NewStdioJudge(executor, log),
			contract.KindJudgeSQL:      NewSqlJudge(executor, log),
			contract.KindJudgeNoSQL:    NewNoSqlJudge(executor, log),
		},
		events: events,
		log:    log,
	}
}

// Judge 는 작업 하나를 처음부터 끝까지 수행한다.
func (s *Service) Judge(ctx context.Context, job contract.JudgeJob) {
	kind, known := s.kinds[job.KindOf()]
	if !known {
		// 우리가 모르는 유형이다. **채점을 시도하지 않는다** — stdin/stdout 으로 넘겨
		// 짐작해 채점하면 엉뚱한 판정이 사용자 기록에 남는다.
		s.log.Error("처리할 수 없는 문제 유형입니다",
			"submissionId", job.SubmissionID, "kind", job.KindOf())
		s.complete(ctx, job, Summary{
			Verdict:    contract.VerdictSystemError,
			TotalCount: len(job.Testcases),
		}, "")
		return
	}

	/*
		함수형인데 하네스가 없다 (#447).

		**짐작해 돌리지 않는다.** 하네스 없이 함수만 있는 코드를 실행하면 출력이 없고,
		그러면 "틀렸다" 로 기록된다 — 사용자 잘못이 아닌데 사용자 기록에 남는다.
	*/
	if job.KindOf() == contract.KindJudgeFunction && job.Harness == "" {
		s.log.Error("함수형 문제인데 하네스가 없습니다", "submissionId", job.SubmissionID)
		s.complete(ctx, job, Summary{
			Verdict:    contract.VerdictSystemError,
			TotalCount: len(job.Testcases),
		}, "")
		return
	}

	// 제약이 잘못된 작업은 테스트케이스를 하나도 돌리지 않고 즉시 종결한다.
	// 실행기에서 케이스마다 같은 오류를 반복해 내는 것보다 낫다.
	if err := contract.ValidateLimits(job.TimeLimitMs, job.MemoryLimitMb); err != nil {
		s.log.Error("실행 제약이 올바르지 않습니다",
			"submissionId", job.SubmissionID, "error", err)
		// 상세 사유는 로그에만 남긴다 — 사용자에게는 채점 인프라 문제로 보이는 편이 정확하다.
		s.complete(ctx, job, Summary{
			Verdict:    contract.VerdictSystemError,
			TotalCount: len(job.Testcases),
		}, "")
		return
	}

	outcome := kind.Judge(ctx, job, func(event contract.Event) { s.publish(ctx, event) })
	s.complete(ctx, job, outcome.Summary, outcome.CompileError)
}

func (s *Service) complete(ctx context.Context, job contract.JudgeJob, summary Summary, compileError string) {
	s.publish(ctx, contract.Event{
		Type:         contract.EventCompleted,
		SubmissionID: job.SubmissionID,
		Verdict:      summary.Verdict,
		PassedCount:  summary.PassedCount,
		TotalCount:   summary.TotalCount,
		MaxRuntimeMs: summary.MaxRuntimeMs,
		MaxMemoryKb:  summary.MaxMemoryKb,
		CompileError: compileError,
	})
	s.log.Info("채점 완료",
		"submissionId", job.SubmissionID, "verdict", summary.Verdict,
		"passed", summary.PassedCount, "total", summary.TotalCount)
}

func (s *Service) publish(ctx context.Context, event contract.Event) {
	if err := s.events.Publish(ctx, event); err != nil {
		s.log.Error("이벤트 발행 실패", "submissionId", event.SubmissionID, "type", event.Type, "error", err)
	}
}

func excerpt(text string) string {
	if len(text) <= stderrExcerptLimit {
		return text
	}
	return text[:stderrExcerptLimit] + "…"
}
