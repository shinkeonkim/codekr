package contract

import (
	"bytes"
	"encoding/json"
	"log/slog"
	"os"
	"regexp"
	"testing"
)

/*
세 앱에 **같은 쿼리 한 벌**이 통해야 한다 (#679).

이 시험이 지키는 것은 api 의 `logstash` 형식과 겹치는 세 키다. 하나라도 어긋나면
Loki 쿼리를 두 벌 쓰게 되고, 한 대시보드에 못 얹는다.
*/
func TestLogHandlerUsesLogstashFieldNames(t *testing.T) {
	var out bytes.Buffer
	slog.New(NewLogHandler(&out)).Error("채점 실패", "submissionId", 42)

	var line map[string]any
	if err := json.Unmarshal(out.Bytes(), &line); err != nil {
		t.Fatalf("JSON 이 아닙니다: %v (%s)", err, out.String())
	}

	// api 의 logstash 형식이 쓰는 이름이다. 값까지 본다 — `level` 은 대문자여야 한다.
	if line["level"] != "ERROR" {
		t.Errorf(`level 이 "ERROR" 가 아닙니다: %v`, line["level"])
	}
	if line["message"] != "채점 실패" {
		t.Errorf("message 가 없습니다: %v", line["message"])
	}
	if _, ok := line["@timestamp"]; !ok {
		t.Errorf("@timestamp 가 없습니다: %v", line)
	}

	// slog 기본 이름이 남아 있으면 두 이름이 섞인다.
	for _, stale := range []string{"msg", "time"} {
		if _, found := line[stale]; found {
			t.Errorf("옛 키 %q 가 남아 있습니다: %v", stale, line)
		}
	}

	// 붙여 준 속성은 그대로 최상위 필드다 — 그래야 `| json | submissionId="42"` 가 된다.
	if line["submissionId"] != float64(42) {
		t.Errorf("속성이 필드로 안 올라갔습니다: %v", line)
	}
}

/*
중첩된 속성의 `msg` 는 건드리지 않는다.

바꿔 버리면 **뜻이 다른 두 값이 같은 이름**이 된다 — 실행기가 남기는 오류 본문 안의
`msg` 가 로그 자신의 메시지 자리로 올라오는 식이다.
*/
func TestLogHandlerLeavesNestedKeysAlone(t *testing.T) {
	var out bytes.Buffer
	slog.New(NewLogHandler(&out)).Info("실행 결과",
		slog.Group("result", slog.String("msg", "컴파일 실패"), slog.String("time", "3s")))

	var line map[string]any
	if err := json.Unmarshal(out.Bytes(), &line); err != nil {
		t.Fatalf("JSON 이 아닙니다: %v", err)
	}

	group, ok := line["result"].(map[string]any)
	if !ok {
		t.Fatalf("그룹이 없습니다: %v", line)
	}
	if group["msg"] != "컴파일 실패" || group["time"] != "3s" {
		t.Errorf("중첩된 키가 바뀌었습니다: %v", group)
	}
}

/*
제출 번호가 api 와 같은 이름으로 나간다 (#681).

**이름이 어긋나면 이을 수 없다.** `submissionId` 와 `submission_id` 는 Loki 에서 서로
다른 필드고, 그러면 쿼리를 두 벌 써야 한다 — 이 이슈가 없애려던 바로 그 상태다.

api 쪽 상수(`Correlation.SUBMISSION`)를 함께 읽어 견준다. Go 시험이 Kotlin 파일을
읽는 것이 이상해 보이지만, **계약이 두 언어에 걸쳐 있으면 그것을 확인할 자리도
한쪽에는 있어야 한다** — 큐 키를 `testdata/queue-keys.json` 으로 견주는 것과 같다.
*/
func TestSubmissionLogKeyMatchesApi(t *testing.T) {
	raw, err := os.ReadFile("../../apps/api/src/main/kotlin/codekr/api/observability/Correlation.kt")
	if err != nil {
		t.Fatalf("api 쪽 상수를 읽지 못했습니다: %v", err)
	}

	match := regexp.MustCompile(`const val SUBMISSION = "([^"]+)"`).FindSubmatch(raw)
	if match == nil {
		t.Fatal("Correlation.SUBMISSION 을 찾지 못했습니다")
	}
	if got := string(match[1]); got != LogKeySubmission {
		t.Fatalf("로그 필드 이름이 다릅니다: api=%q go=%q", got, LogKeySubmission)
	}
}

// 실제로 그 이름으로 나가는지도 본다 — 상수만 맞고 안 붙이면 소용이 없다.
func TestLogHandlerCarriesSubmissionID(t *testing.T) {
	var out bytes.Buffer
	slog.New(NewLogHandler(&out)).With(LogKeySubmission, int64(42)).Info("채점 시작")

	var line map[string]any
	if err := json.Unmarshal(out.Bytes(), &line); err != nil {
		t.Fatalf("JSON 이 아닙니다: %v", err)
	}
	if line[LogKeySubmission] != float64(42) {
		t.Fatalf("제출 번호가 최상위 필드로 안 나옵니다: %v", line)
	}
}
