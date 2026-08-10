package sandbox

import (
	"archive/tar"
	"bytes"
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"io"
	"strconv"
	"strings"
)

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

// buildScript 는 컨테이너 안에서 실행할 래퍼 스크립트를 만든다.
//
// 래퍼가 필요한 이유는 세 가지다.
//   - 컴파일 실패를 실행 실패와 구분해야 한다 (약속된 종료 코드 91).
//   - 시간 제한은 컴파일 시간을 포함하지 않아야 한다 (실행 단계에만 timeout 적용).
//   - 실행 시간과 최대 메모리는 컨테이너 안에서 재야 정확하다 (컨테이너 기동 시간 제외).
func buildScript(spec Spec, nonce string) string {
	var b strings.Builder
	b.WriteString("set -u\ncd " + workDir + "\n")

	if len(spec.Compile) > 0 {
		compileSeconds := seconds(spec.CompileTimeoutMs)
		b.WriteString("if ! timeout -k 1 " + compileSeconds + " " + quoteAll(spec.Compile) + " >/work/.compile 2>&1; then\n")
		b.WriteString("  cat /work/.compile >&2\n")
		fmt.Fprintf(&b, "  printf '\\n%s exit=%d wall_ms=0 mem_bytes=0\\n'\n", nonce, compileFailedExitCode)
		fmt.Fprintf(&b, "  exit %d\n", compileFailedExitCode)
		b.WriteString("fi\n")
	}

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
func buildInputArchive(spec Spec, script string) (io.Reader, error) {
	var buf bytes.Buffer
	writer := tar.NewWriter(&buf)

	files := []struct {
		name string
		body string
		mode int64
	}{
		{spec.SourceFile, spec.SourceCode, 0o644},
		{"input.txt", spec.Stdin, 0o644},
		{"run.sh", script, 0o755},
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
