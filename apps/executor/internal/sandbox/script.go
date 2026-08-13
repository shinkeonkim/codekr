package sandbox

import (
	"archive/tar"
	"bytes"
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"io"
	"sort"
	"strconv"
	"strings"

	"github.com/shinkeonkim/codekr/apps/executor/internal/sandbox/harness"
)

// 샌드박스가 스스로 쓰는 파일 이름. 문제 자료가 이 이름을 쓰면 래퍼 스크립트를
// 덮어써 임의 명령을 실행할 수 있다.
var reservedFiles = map[string]bool{
	"input.txt": true, "compile.sh": true, "run.sh": true,
}

func validateExtraFileName(name string, reserved map[string]bool) error {
	if name == "" || reserved[name] {
		return fmt.Errorf("쓸 수 없는 파일 이름입니다: %q", name)
	}
	// 작업 디렉터리 밖으로 나가는 이름은 받지 않는다.
	if strings.ContainsAny(name, "/\\") || name == "." || name == ".." {
		return fmt.Errorf("파일 이름에 경로를 쓸 수 없습니다: %q", name)
	}
	return nil
}

func sortedNames(files map[string]string) []string {
	names := make([]string, 0, len(files))
	for name := range files {
		names = append(names, name)
	}
	sort.Strings(names)
	return names
}

const (
	// 컴파일 실패를 다른 실패와 구분하기 위한 약속된 종료 코드.
	compileFailedExitCode = 91
	// GNU coreutils `timeout` 이 제한 시간 초과로 프로세스를 끝냈을 때의 종료 코드.
	timeoutExitCode = 124
	// SIGTERM 으로 종료된 경우. BusyBox `timeout` 은 시간 초과를 이 코드로 알린다.
	sigtermExitCode = 143
	// SIGKILL 로 종료된 경우. 메모리 초과(OOM) 또는 TERM 을 무시한 뒤의 강제 종료다.
	sigkillExitCode = 137

	workDir   = "/work"
	stdinFile = "/work/input.txt"
)

// buildCompileScript 는 소스를 컴파일하는 단계의 스크립트를 만든다.
//
// 컴파일을 실행과 분리한 이유는 자원 한도가 다르기 때문이다. 툴체인(go build, dotnet build,
// kotlinc)은 사용자 프로그램보다 훨씬 많은 메모리와 임시 공간을 쓴다. 문제의 메모리 제한을
// 컴파일에까지 적용하면 "256MB 문제"는 Go 로 아예 풀 수 없게 된다 — 제한의 의미가 왜곡된다.
// 그래서 컴파일은 넉넉한 한도에서 돌리고, 끝난 뒤 문제의 한도로 낮춘 다음 실행한다.
//
// 컴파일이 필요 없는 런타임이면 빈 문자열을 돌려준다.
func buildCompileScript(spec Spec, nonce string) string {
	if len(spec.Compile) == 0 {
		return ""
	}

	var b strings.Builder
	b.WriteString("set -u\ncd " + workDir + "\n")
	b.WriteString("if ! timeout -k 1 " + seconds(spec.CompileTimeoutMs) + " " +
		quoteAll(spec.Compile) + " >/work/.compile 2>&1; then\n")
	b.WriteString("  cat /work/.compile >&2\n")
	fmt.Fprintf(&b, "  printf '\\n%s exit=%d wall_ms=0 mem_bytes=0\\n'\n", nonce, compileFailedExitCode)
	fmt.Fprintf(&b, "  exit %d\n", compileFailedExitCode)
	b.WriteString("fi\n")
	// 빌드 캐시는 실행 단계의 메모리 한도를 잡아먹는다 (작업 디렉터리가 tmpfs 다).
	b.WriteString("rm -rf /work/.gocache /work/.compile /work/obj\n")
	return b.String()
}

// buildRunScript 는 사용자 프로그램을 실행하고 계측 값을 남기는 단계의 스크립트를 만든다.
//
// 시간 제한은 이 단계에만 적용된다. 실행 시간과 최대 메모리도 컨테이너 안에서 재야
// 컨테이너 기동 시간이 섞이지 않는다.
func buildRunScript(spec Spec, nonce string) string {
	var b strings.Builder
	b.WriteString("set -u\ncd " + workDir + "\n")
	b.WriteString("START=$(cut -d' ' -f1 /proc/uptime)\n")
	b.WriteString("timeout -k 1 " + seconds(spec.TimeLimitMs) + " " + quoteAll(spec.Run) + " < " + stdinFile + "\n")
	b.WriteString("CODE=$?\n")
	b.WriteString("END=$(cut -d' ' -f1 /proc/uptime)\n")
	// cgroup v2 는 memory.peak, v1 은 max_usage_in_bytes 로 최대 사용량을 노출한다.
	b.WriteString("MEM=0\n")
	b.WriteString("if [ -r /sys/fs/cgroup/memory.peak ]; then MEM=$(cat /sys/fs/cgroup/memory.peak)\n")
	b.WriteString("elif [ -r /sys/fs/cgroup/memory/memory.max_usage_in_bytes ]; then MEM=$(cat /sys/fs/cgroup/memory/memory.max_usage_in_bytes)\n")
	b.WriteString("fi\n")
	b.WriteString("ELAPSED=$(awk -v s=\"$START\" -v e=\"$END\" 'BEGIN{d=(e-s)*1000; if (d<0) d=0; printf \"%d\", d}')\n")
	// 계측 값은 표준 출력 마지막 줄에 실어 보낸다. tmpfs 안의 파일은 컨테이너 밖에서 읽을 수 없다.
	b.WriteString("printf '\\n" + nonce + " exit=%s wall_ms=%s mem_bytes=%s\\n' \"$CODE\" \"$ELAPSED\" \"$MEM\"\n")
	b.WriteString("exit $CODE\n")
	return b.String()
}

// stageScripts 는 단계별 스크립트다. compile 은 컴파일이 필요 없으면 빈 문자열이다.
type stageScripts struct {
	compile string
	run     string
}

// newNonce 는 계측 줄을 사용자 출력과 구분하기 위한 1회용 표식을 만든다.
func newNonce() string {
	buf := make([]byte, 12)
	if _, err := rand.Read(buf); err != nil {
		return "CODEKR-METRICS"
	}
	return "CODEKR-" + hex.EncodeToString(buf)
}

// buildInputArchive 는 소스, 표준 입력, 래퍼 스크립트를 담은 tar 를 base64 로 감싸 돌려준다.
// 컨테이너 표준 입력으로 흘려 넣으면 작업 디렉터리에 그대로 풀린다.
func buildInputArchive(spec Spec, scripts stageScripts) (io.Reader, error) {
	var buf bytes.Buffer
	writer := tar.NewWriter(&buf)

	reserved := map[string]bool{}
	for name := range reservedFiles {
		reserved[name] = true
	}

	// 하네스를 먼저 준비한다 (#60). 사용자·문제 파일이 그 이름을 쓸 수 없게 하기 위함이다.
	harnessFiles, err := harness.Files(spec.Harness)
	if err != nil {
		return nil, err
	}
	for name := range harnessFiles {
		reserved[name] = true
	}

	files := []struct {
		name string
		body string
		mode int64
	}{
		{"input.txt", spec.Stdin, 0o644},
		{"compile.sh", scripts.compile, 0o755},
		{"run.sh", scripts.run, 0o755},
	}

	/*
		제출한 소스 (#457).

		파일 하나면 지금까지와 같다 — `SourceFile` 이름으로 `SourceCode` 를 놓는다.
		여럿이면 그 파일들을 놓되 **진입점이 그 안에 있어야 한다**: 없으면 무엇을
		컴파일·실행할지가 없고, 그 상태로 돌리면 "빈 파일을 짠 결과" 가 판정으로 남는다.
	*/
	sources := spec.SourceFiles
	if len(sources) == 0 {
		sources = map[string]string{spec.SourceFile: spec.SourceCode}
	} else if _, ok := sources[spec.SourceFile]; !ok {
		return nil, fmt.Errorf("진입점 파일이 제출에 없습니다: %q", spec.SourceFile)
	}
	for _, name := range sortedNames(sources) {
		// 사용자가 쓴 이름이다. 예약 이름과 경로 탈출을 여기서 막는다 — 화면이 걸러도
		// API 를 직접 부르는 길이 생기면 그대로 뚫린다.
		if err := validateExtraFileName(name, reserved); err != nil {
			return nil, err
		}
		files = append(files, struct {
			name string
			body string
			mode int64
		}{name, sources[name], 0o644})
		// **사용자 파일끼리도 겹칠 수 없고**, 뒤따르는 문제 자료가 이것을 덮지도 못한다.
		reserved[name] = true
	}

	// 런타임이 필요로 하는 하네스 (#60). 이름은 위에서 이미 예약해 두었다 —
	// 하네스를 덮어쓰면 임의 명령을 실행할 수 있다.
	for _, name := range sortedNames(harnessFiles) {
		files = append(files, struct {
			name string
			body string
			mode int64
		}{name, harnessFiles[name], 0o755})
	}

	// 문제가 싣는 파일 (#60). 예약 이름과 경로 탈출은 미리 막는다 — 이 자료는
	// 출제자가 넣지만, 검증을 어드민 화면에만 두면 화면을 거치지 않는 경로가 생겼을 때
	// 그대로 뚫린다.
	for _, name := range sortedNames(spec.ExtraFiles) {
		if err := validateExtraFileName(name, reserved); err != nil {
			return nil, err
		}
		files = append(files, struct {
			name string
			body string
			mode int64
		}{name, spec.ExtraFiles[name], 0o644})
	}

	for _, file := range files {
		header := &tar.Header{Name: file.name, Mode: file.mode, Size: int64(len(file.body))}
		if err := writer.WriteHeader(header); err != nil {
			return nil, err
		}
		if _, err := io.WriteString(writer, file.body); err != nil {
			return nil, err
		}
	}
	if err := writer.Close(); err != nil {
		return nil, err
	}

	// 임의의 바이트가 셸을 거쳐 안전하게 지나가도록 base64 로 감싼다.
	var encoded bytes.Buffer
	encoder := base64.NewEncoder(base64.StdEncoding, &encoded)
	if _, err := encoder.Write(buf.Bytes()); err != nil {
		return nil, err
	}
	if err := encoder.Close(); err != nil {
		return nil, err
	}
	return &encoded, nil
}

// seconds 는 밀리초를 올림한 초 문자열로 바꾼다. 최소 1초는 보장한다.
func seconds(ms int) string {
	value := (ms + 999) / 1000
	if value < 1 {
		value = 1
	}
	return strconv.Itoa(value)
}

// quoteAll 은 명령 인자를 셸 주입 없이 안전하게 이어 붙인다.
func quoteAll(args []string) string {
	quoted := make([]string, 0, len(args))
	for _, arg := range args {
		quoted = append(quoted, "'"+strings.ReplaceAll(arg, "'", `'\''`)+"'")
	}
	return strings.Join(quoted, " ")
}
