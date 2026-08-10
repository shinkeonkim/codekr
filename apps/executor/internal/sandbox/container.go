package sandbox

import (
	"context"
	"errors"
	"fmt"
	"io"
	"strings"
	"time"

	"github.com/moby/moby/api/pkg/stdcopy"
	"github.com/moby/moby/api/types/container"
	"github.com/moby/moby/client"
)

// 샌드박스 UID/GID. 이미지에 이 계정이 없어도 되도록 숫자로 지정한다.
const sandboxUser = "10001:10001"

// capturedOutput 은 한 번의 실행에서 거둬들인 표준 출력/에러다.
type capturedOutput struct {
	stdout    string
	stderr    string
	truncated bool
}

// runCommand 는 2단계에서 사용자 프로그램을 실행한다.
const runCommand = "cd " + workDir + " && exec sh run.sh"

const bytesPerMb = 1024 * 1024

// unpackCommand 는 표준 입력으로 들어온 base64 tar 를 작업 디렉터리에 풀고,
// 컴파일이 필요한 런타임이면 이어서 컴파일까지 수행한다.
func unpackCommand(scripts stageScripts) string {
	unpack := "cd " + workDir + " && base64 -d > bundle.tar && tar xf bundle.tar && rm -f bundle.tar"
	if scripts.compile == "" {
		return unpack
	}
	return unpack + " && exec sh compile.sh"
}

// containerSandbox 는 컨테이너 런타임에 형제 컨테이너를 띄워 코드를 실행한다.
//
// 런타임과는 OCI 호환 엔진 API 로만 이야기한다. 운영 환경의 런타임은 containerd 이고,
// 로컬 개발 환경은 같은 API 를 노출하는 엔진을 쓴다 — 이 파일은 어느 쪽인지 구분하지 않는다.
// containerd 를 CRI 로 직접 다루는 구현은 별도 과제다 (ADR-0003).
//
// 컨테이너를 `sleep` 으로 먼저 띄운 뒤 작업 파일을 심고 exec 로 실행하는 순서를 쓴다.
// 읽기 전용 rootfs + tmpfs 작업 디렉터리 조합에서는 컨테이너가 시작된 뒤에야
// 작업 디렉터리에 쓸 수 있기 때문이다.
type containerSandbox struct {
	cli *client.Client
}

// NewContainerSandbox 는 컨테이너 런타임 기반 샌드박스를 만든다.
func NewContainerSandbox() (Sandbox, error) {
	cli, err := client.New(client.FromEnv)
	if err != nil {
		return nil, fmt.Errorf("컨테이너 런타임 클라이언트 생성 실패: %w", err)
	}
	return &containerSandbox{cli: cli}, nil
}

// Close 는 런타임 클라이언트가 쥐고 있는 연결을 정리한다.
func (s *containerSandbox) Close() error { return s.cli.Close() }

// Preflight 는 런타임 API 에 닿는지 확인한다.
func (s *containerSandbox) Preflight(ctx context.Context) error {
	if _, err := s.cli.Ping(ctx, client.PingOptions{}); err != nil {
		return fmt.Errorf("컨테이너 런타임에 닿지 못했습니다: %w", err)
	}
	return nil
}

// Run 은 격리된 컨테이너에서 코드를 한 번 실행하고 관찰 결과를 돌려준다.
func (s *containerSandbox) Run(ctx context.Context, spec Spec) (Outcome, error) {
	budget := lifetime(spec)
	ctx, cancel := context.WithTimeout(ctx, budget)
	defer cancel()

	containerID, err := s.create(ctx, spec, budget)
	if err != nil {
		return Outcome{}, err
	}
	defer s.remove(containerID)

	if _, err := s.cli.ContainerStart(ctx, containerID, client.ContainerStartOptions{}); err != nil {
		return Outcome{}, fmt.Errorf("컨테이너 시작 실패: %w", err)
	}

	nonce := newNonce()
	scripts := stageScripts{compile: buildCompileScript(spec, nonce), run: buildRunScript(spec, nonce)}
	payload, err := buildInputArchive(spec, scripts)
	if err != nil {
		return Outcome{}, err
	}

	// 1단계: 작업 파일을 심고, 필요하면 컴파일한다 (넉넉한 메모리 한도).
	captured, err := s.exec(ctx, containerID, unpackCommand(scripts), payload, spec.MaxOutputBytes)
	if err != nil {
		return Outcome{}, err
	}
	if output, collected := splitMetrics(captured.stdout, nonce); collected.present {
		// 계측 줄이 이 단계에서 나왔다면 컴파일이 실패한 것이다.
		return toOutcome(collected, output, captured.stderr, captured.truncated, spec.TimeLimitMs), nil
	}

	// 2단계: 문제의 메모리 한도로 낮춘 뒤 사용자 프로그램을 실행한다.
	if err := s.applyRunMemoryLimit(ctx, containerID, spec); err != nil {
		return Outcome{}, err
	}
	captured, err = s.exec(ctx, containerID, runCommand, nil, spec.MaxOutputBytes)
	if err != nil {
		return Outcome{}, err
	}

	userOutput, collected := splitMetrics(captured.stdout, nonce)
	return toOutcome(collected, userOutput, captured.stderr, captured.truncated, spec.TimeLimitMs), nil
}

// applyRunMemoryLimit 은 컴파일 단계에 열어 둔 여유를 걷고 문제의 메모리 제한을 건다.
// 컴파일이 없었다면 이미 문제의 한도로 만들어졌으므로 아무것도 하지 않는다.
func (s *containerSandbox) applyRunMemoryLimit(ctx context.Context, containerID string, spec Spec) error {
	if len(spec.Compile) == 0 {
		return nil
	}
	memoryBytes := int64(spec.MemoryLimitMb) * bytesPerMb
	_, err := s.cli.ContainerUpdate(ctx, containerID, client.ContainerUpdateOptions{
		Resources: &container.Resources{Memory: memoryBytes, MemorySwap: memoryBytes},
	})
	if err != nil {
		return fmt.Errorf("실행 단계 메모리 제한 적용 실패: %w", err)
	}
	return nil
}

func (s *containerSandbox) create(ctx context.Context, spec Spec, budget time.Duration) (string, error) {
	// 컴파일 단계에 필요한 여유를 먼저 열어 두고, 실행 직전에 문제의 한도로 낮춘다.
	memoryBytes := int64(startupMemoryLimitMb(spec)) * bytesPerMb
	pidsLimit := int64(128)

	created, err := s.cli.ContainerCreate(ctx, client.ContainerCreateOptions{
		Config: &container.Config{
			Image: spec.Image,
			// 파일을 복사할 시간을 벌기 위해 컨테이너 자체는 대기만 한다.
			Entrypoint:      []string{"sleep"},
			Cmd:             []string{fmt.Sprintf("%d", int(budget.Seconds())+5)},
			WorkingDir:      workDir,
			NetworkDisabled: true,
		},
		HostConfig: &container.HostConfig{
			NetworkMode:    "none",
			ReadonlyRootfs: true,
			// 작업 디렉터리만 쓰기 가능하게 열고 나머지는 읽기 전용으로 둔다.
			// 컴파일 산출물과 임시 파일이 여기 쌓인다. tmpfs 사용량은 메모리 한도에도
			// 포함되므로, 실행 단계로 넘어가기 전에 컴파일 캐시를 지운다 (compile.sh).
			Tmpfs: map[string]string{
				workDir: "rw,exec,mode=1777,size=512m",
				"/tmp":  "rw,mode=1777,size=256m",
			},
			Resources: container.Resources{
				Memory: memoryBytes,
				// 스왑을 막아야 메모리 초과가 즉시 OOM 으로 드러난다.
				MemorySwap: memoryBytes,
				NanoCPUs:   1_000_000_000,
				PidsLimit:  &pidsLimit,
			},
			CapDrop:     []string{"ALL"},
			SecurityOpt: []string{"no-new-privileges"},
		},
	})
	if err != nil {
		return "", fmt.Errorf("컨테이너 생성 실패: %w", err)
	}
	return created.ID, nil
}

// exec 는 소스 묶음을 표준 입력으로 흘려 넣고 곧바로 실행까지 한 번에 처리한다.
//
// 파일 복사 API 를 쓰지 않는 이유는, 읽기 전용 rootfs 컨테이너에는 대상이 tmpfs 여도
// 복사가 거부되기 때문이다. 표준 입력 경로는 그 제약을 받지 않으므로
// 읽기 전용 rootfs 를 유지한 채로 작업 파일을 심을 수 있다.
func (s *containerSandbox) exec(
	ctx context.Context,
	containerID string,
	command string,
	payload io.Reader,
	maxOutput int,
) (capturedOutput, error) {
	created, err := s.cli.ExecCreate(ctx, containerID, client.ExecCreateOptions{
		User:         sandboxUser,
		Cmd:          []string{"sh", "-c", command},
		AttachStdin:  payload != nil,
		AttachStdout: true,
		AttachStderr: true,
	})
	if err != nil {
		return capturedOutput{}, fmt.Errorf("exec 생성 실패: %w", err)
	}

	attached, err := s.cli.ExecAttach(ctx, created.ID, client.ExecAttachOptions{})
	if err != nil {
		return capturedOutput{}, fmt.Errorf("exec 연결 실패: %w", err)
	}
	defer attached.Close()

	// 페이로드를 다 보내고 쓰기 방향을 닫아야 컨테이너 쪽 base64 가 EOF 를 본다.
	if payload != nil {
		go func() {
			_, _ = io.Copy(attached.Conn, payload)
			_ = attached.CloseWrite()
		}()
	}

	var outBuf, errBuf strings.Builder
	limit := int64(maxOutput)
	// 출력이 무한정 커지지 않도록 상한을 넘는 부분은 버린다.
	if _, err := stdcopy.StdCopy(
		limitedWriter{&outBuf, limit},
		limitedWriter{&errBuf, limit},
		attached.Reader,
	); err != nil && !errors.Is(err, io.EOF) {
		return capturedOutput{}, fmt.Errorf("출력 수집 실패: %w", err)
	}

	return capturedOutput{
		stdout:    outBuf.String(),
		stderr:    errBuf.String(),
		truncated: int64(outBuf.Len()) >= limit || int64(errBuf.Len()) >= limit,
	}, nil
}

func (s *containerSandbox) remove(containerID string) {
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	_, _ = s.cli.ContainerRemove(ctx, containerID, client.ContainerRemoveOptions{Force: true})
}

// startupMemoryLimitMb 는 컨테이너를 만들 때 걸 메모리 한도다.
// 컴파일이 있는 런타임은 툴체인이 쓸 만큼 열어 둔다 (docs/06_실행_제약_계약.md).
func startupMemoryLimitMb(spec Spec) int {
	if len(spec.Compile) == 0 || spec.CompileMemoryLimitMb <= spec.MemoryLimitMb {
		return spec.MemoryLimitMb
	}
	return spec.CompileMemoryLimitMb
}

// lifetime 은 컨테이너에 허용할 전체 수명이다. 컴파일 + 실행 + 여유를 더한다.
func lifetime(spec Spec) time.Duration {
	total := spec.TimeLimitMs + spec.CompileTimeoutMs + 10_000
	return time.Duration(total) * time.Millisecond
}
