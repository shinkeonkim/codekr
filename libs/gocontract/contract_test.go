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
	// 유형 필드가 없는 옛 작업은 stdin/stdout 으로 읽는다 (#59). api(Kotlin)도 같게 읽는다.
	if job.KindOf() != KindJudgeStdio {
		t.Fatalf("유형이 없으면 stdin/stdout 이어야 합니다: %s", job.KindOf())
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

// 큐 키는 api(Kotlin)와 judge/executor(Go)가 문자열로 맞춰야 하는 계약이다.
// 한쪽만 바꾸면 워커가 아무것도 못 읽는데, 오류 없이 조용히 멈추기 때문에 발견이 늦다.
// 그래서 고정 JSON 을 두고 양쪽이 같은 값을 보는지 확인한다 (#102).
func TestQueueKeysMatchFixture(t *testing.T) {
	raw := readFixture(t, "queue-keys.json")

	var fixture struct {
		JudgeStreamsByPriority []string `json:"judgeStreamsByPriority"`
		JudgeContestStream     string   `json:"judgeContestStream"`
		ExecStream             string   `json:"execStream"`
		JudgeGroup             string   `json:"judgeGroup"`
		ExecGroup              string   `json:"execGroup"`
		EventChannel           string   `json:"eventChannel"`
		ReplyStreamPrefix      string   `json:"replyStreamPrefix"`
		PayloadField           string   `json:"payloadField"`
	}
	if err := json.Unmarshal(raw, &fixture); err != nil {
		t.Fatalf("고정 JSON 파싱 실패: %v", err)
	}

	// 대회 큐는 등급 목록에 넣지 않는다 — 일반 워커가 읽으면 격리가 되지 않는다 (#62).
	if fixture.JudgeContestStream != StreamJudgeContest {
		t.Fatalf("대회 스트림 키가 다릅니다: %s vs %s", fixture.JudgeContestStream, StreamJudgeContest)
	}

	actual := JudgeStreamsByPriority()
	if len(actual) != len(fixture.JudgeStreamsByPriority) {
		t.Fatalf("채점 스트림 개수가 다릅니다: %v vs %v", actual, fixture.JudgeStreamsByPriority)
	}
	for i, want := range fixture.JudgeStreamsByPriority {
		if actual[i] != want {
			t.Errorf("채점 스트림 %d 번이 다릅니다: %q != %q", i, actual[i], want)
		}
	}

	for _, pair := range []struct {
		name          string
		got, expected string
	}{
		{"실행 스트림", StreamExec, fixture.ExecStream},
		{"채점 그룹", GroupJudge, fixture.JudgeGroup},
		{"실행 그룹", GroupExec, fixture.ExecGroup},
		{"이벤트 채널", ChannelEvents, fixture.EventChannel},
		{"응답 스트림 접두사", ReplyStreamPfx, fixture.ReplyStreamPrefix},
		{"payload 필드", MessagePayloadKey, fixture.PayloadField},
	} {
		if pair.got != pair.expected {
			t.Errorf("%s 가 다릅니다: %q != %q", pair.name, pair.got, pair.expected)
		}
	}
}

// SQL 채점 작업도 api(Kotlin)와 같은 고정 JSON 으로 읽는다 (#60).
func TestJudgeJobSQLFixtureMatchesContract(t *testing.T) {
	var job JudgeJob
	decodeStrict(t, readFixture(t, "judge-job-sql.json"), &job)

	if job.KindOf() != KindJudgeSQL {
		t.Fatalf("SQL 유형이어야 합니다: %s", job.KindOf())
	}
	if job.SQL == nil {
		t.Fatal("SQL 블록이 손실되었습니다")
	}
	// 정답은 결과 집합이 아니라 쿼리다 — 시드가 바뀌면 기대 결과도 따라간다.
	if job.SQL.Answer == "" || job.SQL.Schema == "" {
		t.Fatalf("스키마와 정답 쿼리가 있어야 합니다: %+v", job.SQL)
	}
	if !job.SQL.IgnoreRowOrder {
		t.Fatal("행 순서 무시 옵션이 손실되었습니다")
	}
	// SQL 문제의 채점 단위는 정답 쿼리 하나다.
	if len(job.Testcases) != 0 {
		t.Fatalf("테스트케이스가 없어야 합니다: %+v", job.Testcases)
	}
}

// 차선을 잘못 적었을 때 일반 큐를 먹어 치우지 않아야 한다 (#62).
func TestJudgeStreamsForUnknownLaneIsEmpty(t *testing.T) {
	if streams := JudgeStreamsFor("contets"); len(streams) != 0 {
		t.Fatalf("알 수 없는 차선은 아무것도 읽지 않아야 합니다: %v", streams)
	}
}

func TestJudgeStreamsForSeparatesLanes(t *testing.T) {
	general := JudgeStreamsFor(LaneGeneral)
	contest := JudgeStreamsFor(LaneContest)

	for _, stream := range general {
		if stream == StreamJudgeContest {
			t.Fatal("일반 워커가 대회 큐를 읽으면 격리가 되지 않습니다")
		}
	}
	if len(contest) != 1 || contest[0] != StreamJudgeContest {
		t.Fatalf("대회 워커는 대회 큐만 읽어야 합니다: %v", contest)
	}
	// 차선을 적지 않으면 일반이다 — 기존 배포가 그대로 돈다.
	if len(JudgeStreamsFor("")) != len(general) {
		t.Fatal("빈 차선은 일반이어야 합니다")
	}
}
