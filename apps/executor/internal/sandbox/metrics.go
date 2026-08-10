package sandbox

import (
	"strconv"
	"strings"
)

// metrics 는 래퍼 스크립트가 표준 출력 마지막 줄에 실어 보낸 계측 값이다.
type metrics struct {
	exitCode int
	wallMs   int
	memBytes int64
	// present 는 계측 줄을 찾지 못한 경우(강제 종료 등)를 구분한다.
	present bool
}

// splitMetrics 는 표준 출력에서 계측 줄을 떼어내 사용자 출력과 분리한다.
//
// 표식이 여러 번 나타나면 마지막 것을 쓴다. 계측 줄은 사용자 프로그램이 끝난 뒤에
// 출력되므로, 프로그램이 표식을 흉내 내더라도 항상 진짜가 마지막이다.
func splitMetrics(stdout, nonce string) (string, metrics) {
	index := strings.LastIndex(stdout, nonce)
	if index < 0 {
		return stdout, metrics{}
	}

	line, _, _ := strings.Cut(stdout[index+len(nonce):], "\n")
	// 계측 줄 앞에 붙여 둔 개행까지 제거해 사용자 출력을 원형 그대로 남긴다.
	userOutput := strings.TrimSuffix(stdout[:index], "\n")
	return userOutput, parseMetrics(line)
}

func parseMetrics(line string) metrics {
	parsed := metrics{present: true}
	for _, field := range strings.Fields(line) {
		key, value, found := strings.Cut(field, "=")
		if !found {
			continue
		}
		switch key {
		case "exit":
			parsed.exitCode, _ = strconv.Atoi(value)
		case "wall_ms":
			parsed.wallMs, _ = strconv.Atoi(value)
		case "mem_bytes":
			parsed.memBytes, _ = strconv.ParseInt(value, 10, 64)
		}
	}
	return parsed
}

// toOutcome 은 계측 값과 종료 코드를 실행 결과로 해석한다.
//
// 종료 코드 규약(script.go 의 래퍼가 보장한다):
//   - 91       : 컴파일 실패
//   - 124/143  : timeout 이 시간 제한 초과로 종료 (구현에 따라 코드가 다르다)
//   - 137      : SIGKILL — 메모리 초과(OOM) 또는 TERM 을 무시한 프로세스의 강제 종료
//
// 137 은 두 원인이 겹치므로 실행 시간으로 가른다. 제한 시간을 거의 채우고 죽었다면
// 시간 초과, 그 전에 죽었다면 메모리 초과로 본다.
func toOutcome(m metrics, stdout, stderr string, truncated bool, timeLimitMs int) Outcome {
	outcome := Outcome{
		Stdout:    stdout,
		Stderr:    stderr,
		Truncated: truncated,
		RuntimeMs: m.wallMs,
		MemoryKb:  int(m.memBytes / 1024),
	}

	if !m.present {
		// 계측 줄조차 남기지 못했다면 컨테이너 수명이 먼저 끝난 것이다.
		outcome.TimedOut = true
		return outcome
	}

	outcome.ExitCode = m.exitCode
	switch m.exitCode {
	case compileFailedExitCode:
		outcome.CompileFailed = true
	case timeoutExitCode, sigtermExitCode:
		outcome.TimedOut = true
	case sigkillExitCode:
		if reachedTimeLimit(m.wallMs, timeLimitMs) {
			outcome.TimedOut = true
		} else {
			outcome.OutOfMemory = true
		}
	}
	return outcome
}

// reachedTimeLimit 은 제한 시간을 사실상 소진했는지 본다. 계측 해상도(10ms)를 감안해 여유를 둔다.
func reachedTimeLimit(wallMs, timeLimitMs int) bool {
	return timeLimitMs > 0 && wallMs+50 >= timeLimitMs
}

// limitedWriter 는 상한을 넘는 출력을 조용히 버린다.
type limitedWriter struct {
	target *strings.Builder
	limit  int64
}

func (w limitedWriter) Write(p []byte) (int, error) {
	remaining := w.limit - int64(w.target.Len())
	if remaining <= 0 {
		return len(p), nil
	}
	if int64(len(p)) > remaining {
		w.target.Write(p[:remaining])
		return len(p), nil
	}
	w.target.Write(p)
	return len(p), nil
}
