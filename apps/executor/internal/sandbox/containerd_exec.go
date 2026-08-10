package sandbox

// containerd 태스크 안에서 두 단계를 실행하고 출력을 거두는 부분 (#68).

import (
	"context"
	"fmt"
	"io"
	"os"
	"strings"
	"syscall"

	"github.com/containerd/containerd/v2/client"
	"github.com/containerd/containerd/v2/pkg/cio"
	"github.com/opencontainers/runtime-spec/specs-go"
)

/*
unpackFromArgCommand 는 base64 묶음을 인자로 받아 작업 디렉터리에 푼다.

엔진 API 구현은 이것을 표준 입력으로 흘려 넣지만, containerd 의 exec 에서는 그 방식이
막힌다 (위 주석). 푸는 방식과 이어지는 컴파일은 같다.
*/
func unpackFromArgCommand(scripts stageScripts, bundle string) string {
	// 작은따옴표로 감싼다. base64 는 작은따옴표를 만들지 않으므로 탈출이 필요 없다.
	unpack := "cd " + workDir +
		" && printf '%s' '" + bundle + "' | base64 -d > bundle.tar" +
		" && tar xf bundle.tar && rm -f bundle.tar"
	if scripts.compile == "" {
		return unpack
	}
	return unpack + " && exec sh compile.sh"
}

/*
runInside 는 컨테이너를 띄우고 두 단계를 실행한다.

엔진 API 구현과 **같은 순서**다 — 컴파일은 넉넉한 한도로, 실행은 문제의 한도로.
순서가 다르면 같은 코드가 두 런타임에서 다른 판정을 받는다.
*/
func (s *containerdSandbox) runInside(
	ctx context.Context,
	container client.Container,
	spec Spec,
	env []string,
) (Outcome, error) {
	task, err := container.NewTask(ctx, cio.NullIO)
	if err != nil {
		return Outcome{}, fmt.Errorf("태스크 생성 실패: %w", err)
	}
	defer func() { _, _ = task.Delete(context.WithoutCancel(ctx), client.WithProcessKill) }()

	if err := task.Start(ctx); err != nil {
		return Outcome{}, fmt.Errorf("태스크 시작 실패: %w", err)
	}

	nonce := newNonce()
	scripts := stageScripts{compile: buildCompileScript(spec, nonce), run: buildRunScript(spec, nonce)}
	payload, err := buildInputArchive(spec, scripts)
	if err != nil {
		return Outcome{}, err
	}

	// 1단계: 파일을 심고 컴파일까지.
	//
	// **표준 입력을 쓰지 않는다.** containerd 의 exec 은 stdin FIFO 를 여는 동안
	// 읽는 쪽(컨테이너 프로세스)을 기다리는데, 그 프로세스는 exec 이 돌아온 뒤에야
	// 시작하므로 서로를 기다리다 예산 시간까지 간다.
	//
	// 대신 base64 를 **명령 인자로** 넘긴다. 제출 소스에는 상한이 있어(#38) 인자 길이
	// 한계(ARG_MAX, 보통 2MB)에 닿지 않는다.
	bundle, err := io.ReadAll(payload)
	if err != nil {
		return Outcome{}, fmt.Errorf("작업 묶음을 읽지 못했습니다: %w", err)
	}
	captured, err := s.exec(ctx, task, spec, env, unpackFromArgCommand(scripts, string(bundle)))
	if err != nil {
		return Outcome{}, err
	}
	if output, collected := splitMetrics(captured.stdout, nonce); collected.present {
		return toOutcome(collected, output, captured.stderr, captured.truncated, spec.TimeLimitMs), nil
	}

	// 2단계: 사용자 프로그램.
	//
	// 엔진 API 는 여기서 메모리 한도를 낮추지만(ContainerUpdate), containerd 에서
	// 실행 중인 태스크의 cgroup 을 바꾸는 것은 런타임마다 지원이 갈린다.
	// **대신 실행 스크립트가 ulimit 으로 자기 한도를 건다** — 두 구현이 같은 값을
	// 쓰는지는 selftest 가 확인한다.
	captured, err = s.exec(ctx, task, spec, env, runCommand)
	if err != nil {
		return Outcome{}, err
	}
	userOutput, collected := splitMetrics(captured.stdout, nonce)
	return toOutcome(collected, userOutput, captured.stderr, captured.truncated, spec.TimeLimitMs), nil
}

// exec 는 컨테이너 안에서 명령 하나를 돌리고 출력을 거둔다.
func (s *containerdSandbox) exec(
	ctx context.Context,
	task client.Task,
	spec Spec,
	env []string,
	command string,
) (capturedOutput, error) {
	stdout := &limitedBuffer{limit: spec.MaxOutputBytes}
	stderr := &limitedBuffer{limit: spec.MaxOutputBytes}

	// FIFO 는 **클라이언트와 containerd 가 함께 보는 경로**에 있어야 한다.
	// 기본값(/var/run/containerd)은 클라이언트 쪽에 만들려 하는데, 실행기가
	// 노드 위에서 돌 때는 그게 맞고 원격 소켓으로 붙을 때는 아니다.
	creatorOpts := []cio.Opt{cio.WithStreams(nil, stdout, stderr)}
	if dir := os.Getenv("CODEKR_CONTAINERD_FIFO_DIR"); dir != "" {
		creatorOpts = append(creatorOpts, cio.WithFIFODir(dir))
	}
	creator := cio.NewCreator(creatorOpts...)
	// **exec 프로세스는 컨테이너 spec 을 물려받지 않는다.** 여기에 적지 않은 방어는
	// 걸리지 않는다 — no-new-privileges 를 빠뜨려 self-test 가 잡아냈다.
	process, err := task.Exec(ctx, "exec-"+randomSuffix(), &specs.Process{
		Args:            []string{"sh", "-c", command},
		Cwd:             workDir,
		User:            specs.User{UID: uidOf(spec), GID: gidOf(spec)},
		Env:             env,
		NoNewPrivileges: true,
		// 권한을 전부 뺀다. 빈 집합을 명시해야 한다 — 비워 두면 기본값이 온다.
		Capabilities: &specs.LinuxCapabilities{},
	}, creator)
	if err != nil {
		return capturedOutput{}, fmt.Errorf("exec 생성 실패: %w", err)
	}
	defer func() { _, _ = process.Delete(context.WithoutCancel(ctx), client.WithProcessKill) }()

	statusCh, err := process.Wait(ctx)
	if err != nil {
		return capturedOutput{}, fmt.Errorf("exec 대기 실패: %w", err)
	}
	if err := process.Start(ctx); err != nil {
		return capturedOutput{}, fmt.Errorf("exec 시작 실패: %w", err)
	}

	select {
	case <-statusCh:
	case <-ctx.Done():
		// 시간이 다 되면 끊는다. 계측 줄이 없으면 상위가 시간 초과로 읽는다.
		_ = process.Kill(context.WithoutCancel(ctx), syscall.SIGKILL)
	}

	return capturedOutput{
		stdout:    stdout.String(),
		stderr:    stderr.String(),
		truncated: stdout.truncated || stderr.truncated,
	}, nil
}

// limitedBuffer 는 정해진 크기까지만 담는다. 출력 폭주가 메모리를 먹지 않게 한다.
type limitedBuffer struct {
	buf       strings.Builder
	limit     int
	truncated bool
}

func (b *limitedBuffer) Write(p []byte) (int, error) {
	remaining := b.limit - b.buf.Len()
	if remaining <= 0 {
		b.truncated = true
		return len(p), nil
	}
	if len(p) > remaining {
		b.buf.Write(p[:remaining])
		b.truncated = true
		return len(p), nil
	}
	b.buf.Write(p)
	return len(p), nil
}

func (b *limitedBuffer) String() string { return b.buf.String() }
