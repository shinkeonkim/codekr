package contract

import (
	"io"
	"log/slog"
)

/*
로그 필드 이름을 세 앱이 맞춘다 (#679).

**로그 수집은 이미 된다.** 홈랩의 Promtail 이 모든 파드 로그를 Loki 로 보낸다.
못 하던 것은 **고르는 것**이었고, 그것을 막던 것은 형식이 앱마다 달랐다는 점이다.

api 는 Spring Boot 의 `logstash` 형식으로 간다 — 세 형식 중 그것만 `level` 키에
`"ERROR"` 를 담아 slog 와 겹친다(`ecs` 는 `log.level`, `gelf` 는 숫자다). 남은 차이가
`msg`·`time` 이라 **그쪽이 아니라 이쪽을 고친다** — 고칠 곳이 두 줄이고, 반대로 하면
api 쪽에 우리가 만든 형식 구현을 하나 들여야 한다.

	api        {"@timestamp":…, "level":"ERROR", "message":…, "logger_name":…}
	judge      {"@timestamp":…, "level":"ERROR", "message":…}
	executor   {"@timestamp":…, "level":"ERROR", "message":…}

`{namespace="codekr"} | json | level="ERROR"` 한 벌이 셋 다에 통하는 것이 목적이다.
*/
func NewLogHandler(w io.Writer) slog.Handler {
	return slog.NewJSONHandler(w, &slog.HandlerOptions{ReplaceAttr: renameToLogstash})
}

// renameToLogstash 는 slog 기본 키를 api 쪽 이름으로 바꾼다.
//
// **최상위 키만 바꾼다.** `groups` 가 비어 있지 않다는 것은 중첩된 속성이라는 뜻이고,
// 거기에도 `msg` 라는 이름을 쓴 로그가 있으면 뜻이 다른 값을 같은 이름으로 만든다.
func renameToLogstash(groups []string, attr slog.Attr) slog.Attr {
	if len(groups) > 0 {
		return attr
	}
	switch attr.Key {
	case slog.TimeKey:
		attr.Key = "@timestamp"
	case slog.MessageKey:
		attr.Key = "message"
	}
	return attr
}
