package sandbox

import (
	"strings"
	"testing"
)

func TestBuildScriptAppliesTimeLimitOnlyToRunStage(t *testing.T) {
	script := buildScript(Spec{
		Compile:          []string{"g++", "-o", "main", "main.cpp"},
		Run:              []string{"./main"},
		TimeLimitMs:      2000,
		CompileTimeoutMs: 15000,
	}, "NONCE")

	if !strings.Contains(script, "timeout -k 1 15 'g++'") {
		t.Fatalf("컴파일 단계에 컴파일 타임아웃이 적용되지 않았습니다:\n%s", script)
	}
	if !strings.Contains(script, "timeout -k 1 2 './main'") {
		t.Fatalf("실행 단계에 시간 제한이 적용되지 않았습니다:\n%s", script)
	}
}

func TestBuildScriptSkipsCompileStageForInterpretedRuntimes(t *testing.T) {
	script := buildScript(Spec{Run: []string{"python3", "main.py"}, TimeLimitMs: 1000}, "NONCE")

	if strings.Contains(script, ".compile") {
		t.Fatalf("컴파일 단계가 없어야 합니다:\n%s", script)
	}
}

func TestQuoteAllBlocksShellInjection(t *testing.T) {
	quoted := quoteAll([]string{"python3", "main.py; rm -rf /"})

	if !strings.Contains(quoted, `'main.py; rm -rf /'`) {
		t.Fatalf("인자가 따옴표로 보호되지 않았습니다: %s", quoted)
	}
}

func TestSecondsRoundsUpAndKeepsMinimumOne(t *testing.T) {
	cases := map[int]string{0: "1", 1: "1", 999: "1", 1000: "1", 1001: "2", 2500: "3"}
	for ms, expected := range cases {
		if got := seconds(ms); got != expected {
			t.Errorf("seconds(%d) = %s, 기대값 %s", ms, got, expected)
		}
	}
}

func TestSplitMetricsSeparatesUserOutputFromMeasurements(t *testing.T) {
	stdout := "3\n\nNONCE exit=124 wall_ms=2010 mem_bytes=1048576\n"

	userOutput, parsed := splitMetrics(stdout, "NONCE")

	if userOutput != "3\n" {
		t.Fatalf("사용자 출력이 보존되지 않았습니다: %q", userOutput)
	}
	if !parsed.present || parsed.exitCode != 124 || parsed.wallMs != 2010 || parsed.memBytes != 1048576 {
		t.Fatalf("계측 파싱 결과가 올바르지 않습니다: %+v", parsed)
	}
}

func TestSplitMetricsIgnoresForgedMarkerBeforeTheRealOne(t *testing.T) {
	// 사용자 프로그램이 표식을 흉내 내도 진짜 계측 줄이 항상 마지막에 온다.
	stdout := "NONCE exit=0 wall_ms=1 mem_bytes=1\n\nNONCE exit=137 wall_ms=50 mem_bytes=2048\n"

	_, parsed := splitMetrics(stdout, "NONCE")

	if parsed.exitCode != 137 {
		t.Fatalf("마지막 계측 줄을 써야 합니다: %+v", parsed)
	}
}

func TestToOutcomeMapsExitCodesToFailureKinds(t *testing.T) {
	cases := []struct {
		exitCode int
		wallMs   int
		check    func(Outcome) bool
		name     string
	}{
		{compileFailedExitCode, 0, func(o Outcome) bool { return o.CompileFailed }, "컴파일 실패"},
		{timeoutExitCode, 2000, func(o Outcome) bool { return o.TimedOut }, "시간 초과(GNU)"},
		{sigtermExitCode, 2000, func(o Outcome) bool { return o.TimedOut }, "시간 초과(BusyBox)"},
		{sigkillExitCode, 120, func(o Outcome) bool { return o.OutOfMemory }, "메모리 초과"},
		{sigkillExitCode, 1990, func(o Outcome) bool { return o.TimedOut }, "TERM 무시 후 강제 종료"},
		{0, 10, func(o Outcome) bool { return !o.TimedOut && !o.OutOfMemory && !o.CompileFailed }, "정상"},
	}

	for _, c := range cases {
		outcome := toOutcome(metrics{exitCode: c.exitCode, wallMs: c.wallMs, present: true}, "", "", false, 2000)
		if !c.check(outcome) {
			t.Errorf("%s 로 해석되지 않았습니다: %+v", c.name, outcome)
		}
	}
}

func TestToOutcomeTreatsMissingMetricsAsTimeout(t *testing.T) {
	// 계측 파일조차 남기지 못했다면 컨테이너가 먼저 강제 종료된 것이다.
	if outcome := toOutcome(metrics{}, "", "", false, 2000); !outcome.TimedOut {
		t.Fatalf("계측 파일이 없으면 시간 초과로 봐야 합니다: %+v", outcome)
	}
}
