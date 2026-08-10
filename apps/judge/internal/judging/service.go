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

// Service 는 채점 작업 하나를 처음부터 끝까지 수행한다.
type Service struct {
	executor ExecutorClient
	events   EventSink
	log      *slog.Logger
}

// NewService 는 채점 서비스를 만든다.
func NewService(executor ExecutorClient, events EventSink, log *slog.Logger) *Service {
	return &Service{executor: executor, events: events, log: log}
}

// Judge 는 테스트케이스를 순서대로 실행하고 진행 상황을 이벤트로 알린다.
//
// 첫 실패에서 멈추지 않고 끝까지 채점한다 — 학습자에게는 "몇 개를 통과했는가"가
// 중요한 정보이기 때문이다. 다만 컴파일 실패는 이후 케이스가 전부 같은 결과이므로
// 즉시 종료한다.
func (s *Service) Judge(ctx context.Context, job contract.JudgeJob) {
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

	total := len(job.Testcases)
	s.publish(ctx, contract.Event{
		Type: contract.EventJudging, SubmissionID: job.SubmissionID, TotalCount: total,
	})

	accumulator := NewAccumulator(total)
	for _, testcase := range job.Testcases {
		result := s.runTestcase(ctx, job, testcase)
		verdict := VerdictOf(result, testcase.ExpectedOutput)
		accumulator.Add(verdict, result.RuntimeMs, result.MemoryKb)

		s.publish(ctx, contract.Event{
			Type:          contract.EventTestcase,
			SubmissionID:  job.SubmissionID,
			Seq:           testcase.Seq,
			Verdict:       verdict,
			RuntimeMs:     result.RuntimeMs,
			MemoryKb:      result.MemoryKb,
			StderrExcerpt: excerpt(result.Stderr),
		})

		if verdict == contract.VerdictCompileError {
			s.complete(ctx, job, accumulator.Summarize(), excerpt(result.Stderr))
			return
		}
	}

	s.complete(ctx, job, accumulator.Summarize(), "")
}

func (s *Service) runTestcase(
	ctx context.Context,
	job contract.JudgeJob,
	testcase contract.JudgeTestcase,
) contract.ExecResult {
	result, err := s.executor.Run(ctx, contract.ExecJob{
		RuntimeID:     job.RuntimeID,
		SourceCode:    job.SourceCode,
		Stdin:         testcase.Input,
		TimeLimitMs:   job.TimeLimitMs,
		MemoryLimitMb: job.MemoryLimitMb,
	})
	if err != nil {
		// 실행기가 응답하지 않아도 제출을 미결 상태로 두지 않는다.
		s.log.Error("실행 요청 실패",
			"submissionId", job.SubmissionID, "seq", testcase.Seq, "error", err)
		return contract.ExecResult{Status: contract.StatusSystemError, Stderr: err.Error()}
	}
	return result
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
