package contract

import (
	"encoding/json"
	"os"
	"testing"
)

// 큐 메시지는 Kotlin(api)과 Go(judge/executor)가 나눠 쓰는 계약이다.
// 같은 고정 JSON 을 양쪽에서 읽어, 필드 이름이나 단위가 한쪽만 바뀌는 일을 막는다.
// Kotlin 쪽 짝은 apps/api 의 QueueContractTest 다.

func readFixture(t *testing.T, name string) []byte {
	t.Helper()
	raw, err := os.ReadFile("testdata/" + name)
	if err != nil {
		t.Fatalf("고정 JSON 을 읽지 못했습니다: %v", err)
	}
	return raw
}

func TestJudgeJobFixtureMatchesContract(t *testing.T) {
	var job JudgeJob
	decodeStrict(t, readFixture(t, "judge-job.json"), &job)

	if job.SubmissionID != 1024 || job.RuntimeID != "python:3.12" {
		t.Fatalf("채점 작업 파싱 결과가 다릅니다: %+v", job)
	}
	if job.TimeLimitMs != 2000 || job.MemoryLimitMb != 256 {
		t.Fatalf("실행 제약이 손실되었습니다: %+v", job)
	}
	if len(job.Testcases) != 1 || job.Testcases[0].ExpectedOutput != "3\n" {
		t.Fatalf("테스트케이스가 손실되었습니다: %+v", job.Testcases)
	}
}

func TestExecJobFixtureMatchesContract(t *testing.T) {
	var job ExecJob
	decodeStrict(t, readFixture(t, "exec-job.json"), &job)

	if job.ReplyStream != ReplyStreamPfx+job.JobID {
		t.Fatalf("응답 스트림 규칙이 어긋납니다: %+v", job)
	}
	if job.TimeLimitMs != 2000 || job.MemoryLimitMb != 256 {
		t.Fatalf("실행 제약이 손실되었습니다: %+v", job)
	}
}

func TestExecResultFixtureMatchesContract(t *testing.T) {
	var result ExecResult
	decodeStrict(t, readFixture(t, "exec-result.json"), &result)

	if result.Status != StatusOK || result.RuntimeMs != 24 || result.MemoryKb != 8192 {
		t.Fatalf("실행 결과 파싱이 다릅니다: %+v", result)
	}
}

func TestJudgeEventFixtureMatchesContract(t *testing.T) {
	var event Event
	decodeStrict(t, readFixture(t, "judge-event.json"), &event)

	if event.Type != EventCompleted || event.Verdict != VerdictWrongAnswer {
		t.Fatalf("이벤트 파싱이 다릅니다: %+v", event)
	}
	if event.PassedCount != 2 || event.TotalCount != 3 {
		t.Fatalf("집계 값이 손실되었습니다: %+v", event)
	}
}

// decodeStrict 는 계약에 없는 필드가 있으면 실패한다 — 한쪽이 필드 이름을 바꾸면 바로 드러난다.
func decodeStrict(t *testing.T, raw []byte, target any) {
	t.Helper()
	decoder := json.NewDecoder(newReader(raw))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		t.Fatalf("계약과 맞지 않는 JSON 입니다: %v", err)
	}
}
