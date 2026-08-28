package contract

import (
	"bytes"
	"encoding/json"
	"log/slog"
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
