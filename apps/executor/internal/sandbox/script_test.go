package sandbox

import (
	"strings"
	"testing"
)

func TestCompileAndRunStagesUseTheirOwnTimeouts(t *testing.T) {
	spec := Spec{
		Compile:          []string{"g++", "-o", "main", "main.cpp"},
		Run:              []string{"./main"},
		TimeLimitMs:      2000,
		CompileTimeoutMs: 15000,
	}

	compile := buildCompileScript(spec, "NONCE")
	run := buildRunScript(spec, "NONCE")

	if !strings.Contains(compile, "timeout -k 1 15 'g++'") {
		t.Fatalf("컴파일 단계에 컴파일 타임아웃이 적용되지 않았습니다:\n%s", compile)
	}
	if !strings.Contains(run, "timeout -k 1 2 './main'") {
		t.Fatalf("실행 단계에 시간 제한이 적용되지 않았습니다:\n%s", run)
	}
	// 문제의 시간 제한이 컴파일까지 잡아먹으면 안 된다.
	if strings.Contains(compile, "timeout -k 1 2 ") {
		t.Fatalf("컴파일에 문제의 시간 제한이 적용되었습니다:\n%s", compile)
	}
}

func TestCompileScriptIsEmptyForInterpretedRuntimes(t *testing.T) {
	if compile := buildCompileScript(Spec{Run: []string{"python3", "main.py"}}, "NONCE"); compile != "" {
		t.Fatalf("컴파일 단계가 없어야 합니다:\n%s", compile)
	}
}

func TestCompileScriptClearsBuildCacheBeforeRunStage(t *testing.T) {
	compile := buildCompileScript(Spec{
		Compile: []string{"go", "build"}, Run: []string{"./main"}, CompileTimeoutMs: 15000,
	}, "NONCE")

	// 빌드 캐시가 남으면 tmpfs 를 통해 실행 단계의 메모리 한도를 잡아먹는다.
	if !strings.Contains(compile, "rm -rf /work/.gocache") {
		t.Fatalf("빌드 캐시를 정리하지 않습니다:\n%s", compile)
	}
}

func TestStartupMemoryLimitOpensHeadroomOnlyForCompiledRuntimes(t *testing.T) {
	interpreted := Spec{MemoryLimitMb: 256, CompileMemoryLimitMb: 1024, Run: []string{"python3"}}
	if got := startupMemoryLimitMb(interpreted); got != 256 {
		t.Errorf("컴파일이 없으면 문제의 한도를 그대로 써야 합니다: %d", got)
	}

	compiled := Spec{MemoryLimitMb: 256, CompileMemoryLimitMb: 1024, Compile: []string{"go", "build"}}
	if got := startupMemoryLimitMb(compiled); got != 1024 {
		t.Errorf("컴파일 단계는 여유 한도를 써야 합니다: %d", got)
	}

	generous := Spec{MemoryLimitMb: 2048, CompileMemoryLimitMb: 1024, Compile: []string{"go", "build"}}
	if got := startupMemoryLimitMb(generous); got != 2048 {
		t.Errorf("문제 한도가 더 크면 그쪽을 써야 합니다: %d", got)
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

// 문제 자료가 샌드박스의 래퍼 스크립트를 덮어쓰면 임의 명령을 실행할 수 있다 (#60).
//
// 검증을 어드민 화면에만 두면 화면을 거치지 않는 경로가 생겼을 때 그대로 뚫린다.
// 그래서 샌드박스가 스스로 거부한다.
func TestBuildInputArchiveRejectsFilesThatOverwriteWrappers(t *testing.T) {
	for _, name := range []string{"run.sh", "compile.sh", "input.txt", "run-sql.sh"} {
		spec := Spec{
			SourceFile: "query.sql",
			SourceCode: "SELECT 1;",
			Harness:    "sql",
			ExtraFiles: map[string]string{name: "echo pwned"},
		}
		if _, err := buildInputArchive(spec, stageScripts{}); err == nil {
			t.Fatalf("%q 는 거부해야 합니다", name)
		}
	}
}

func TestBuildInputArchiveRejectsPathsInFileNames(t *testing.T) {
	for _, name := range []string{"../escape.sql", "sub/dir.sql", "..", ""} {
		spec := Spec{
			SourceFile: "query.sql",
			SourceCode: "SELECT 1;",
			ExtraFiles: map[string]string{name: "x"},
		}
		if _, err := buildInputArchive(spec, stageScripts{}); err == nil {
			t.Fatalf("%q 는 거부해야 합니다", name)
		}
	}
}

func TestBuildInputArchiveRejectsUnknownHarness(t *testing.T) {
	spec := Spec{SourceFile: "main.py", SourceCode: "print(3)", Harness: "없는하네스"}
	if _, err := buildInputArchive(spec, stageScripts{}); err == nil {
		t.Fatal("알 수 없는 하네스는 거부해야 합니다")
	}
}
