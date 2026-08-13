// 코드 실행기 진입점. 실행 큐를 소비해 격리 환경에서 코드를 돌린다.
package main

import (
	"context"
	"flag"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/redis/go-redis/v9"
	"github.com/shinkeonkim/codekr/apps/executor/internal/config"
	"github.com/shinkeonkim/codekr/apps/executor/internal/httpapi"
	"github.com/shinkeonkim/codekr/apps/executor/internal/readiness"
	"github.com/shinkeonkim/codekr/apps/executor/internal/runtimes"
	"github.com/shinkeonkim/codekr/apps/executor/internal/sandbox"
	"github.com/shinkeonkim/codekr/apps/executor/internal/selftest"
	"github.com/shinkeonkim/codekr/apps/executor/internal/worker"
)

// main 은 종료 코드만 정한다. 실제 구동은 run 이 한다 —
// os.Exit 는 defer 를 건너뛰므로, 정리해야 할 자원이 있는 코드와 섞이면 안 된다.
func main() {
	os.Exit(run())
}

func run() int {
	// 샌드박스 방어는 노드마다 결과가 다를 수 있어서(런타임·커널·cgroup 설정),
	// 배포된 곳에서 직접 확인할 수단이 필요하다 (#45, docs/09 §5).
	selfTest := flag.Bool("self-test", false, "샌드박스 방어를 검증하고 종료한다")
	// 격리와 준비 상태는 다른 질문이다 (#218). 합치면 실패했을 때 무엇이 잘못됐는지 흐려진다.
	verifyRuntimes := flag.Bool(
		"verify-runtimes",
		false,
		"정의 파일의 모든 런타임이 이 노드에서 도는지 확인하고 종료한다",
	)
	flag.Parse()

	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	cfg := config.Load()

	box, err := sandbox.New(cfg.SandboxRuntime, cfg.SeccompProfilePath)
	if err != nil {
		log.Error("샌드박스 초기화 실패", "runtime", cfg.SandboxRuntime, "error", err)
		return 1
	}
	defer func() { _ = box.Close() }()

	// 런타임에 닿지 못하는 실행기가 healthy 로 떠서 모든 제출을 실패시키는 것을 막는다.
	preflightCtx, cancelPreflight := context.WithTimeout(context.Background(), 10*time.Second)
	err = box.Preflight(preflightCtx)
	cancelPreflight()
	if err != nil {
		log.Error("컨테이너 런타임 확인 실패", "runtime", cfg.SandboxRuntime, "error", err)
		return 1
	}

	// 검사도 정의 파일을 읽는다 (#218). 박아 둔 이미지로 검사하면 정의 파일과 노드가
	// 어긋나도 전부 통과한다 — 그래서 실제로 한 번 놓쳤다 (PR #217).
	registry, err := runtimes.Load(cfg.RuntimesPath)
	if err != nil {
		log.Error("런타임 정의 로드 실패", "error", err)
		return 1
	}

	if *selfTest {
		return runSelfTest(box, registry, cfg.RuntimeRegistry, log)
	}
	if *verifyRuntimes {
		return runVerifyRuntimes(box, registry, cfg.RuntimeRegistry, log)
	}

	// 서비스를 시작하기 전에 방어를 확인한다 (#246).
	//
	// 자동 배포에서는 사람이 `--self-test` 를 돌려 보는 단계가 없다. 그것을 기동에
	// 붙여, **통과하지 못한 실행기는 채점을 받지 않게** 한다 — 뚫린 채로 도는 것보다
	// 파드가 뜨지 않는 편이 낫다. 배포는 눈에 띄게 실패하고 앞 버전이 계속 돈다.
	if cfg.SelfTestOnStart {
		if code := runSelfTest(box, registry, cfg.RuntimeRegistry, log); code != 0 {
			return code
		}
	}

	redisClient := redis.NewClient(&redis.Options{Addr: cfg.RedisAddr})
	defer func() { _ = redisClient.Close() }()

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	server := httpapi.NewServer(cfg.HTTPAddr, redisClient)
	go func() {
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Error("HTTP 서버 종료", "error", err)
		}
	}()

	log.Info("코드 실행기 시작",
		"redis", cfg.RedisAddr, "http", cfg.HTTPAddr, "sandbox", cfg.SandboxRuntime,
		"concurrency", cfg.Concurrency, "runtimes", registry.Images())

	runner := worker.NewRunner(
		registry,
		box,
		cfg.CompileTimeoutMs,
		cfg.CompileMemoryLimitMb,
		cfg.MaxOutputBytes,
		cfg.RuntimeRegistry,
	)
	consumer := worker.NewConsumer(redisClient, runner, cfg.ConsumerName, cfg.Concurrency, log)
	exitCode := 0
	if err := consumer.Start(ctx, cfg.Drain); err != nil {
		log.Error("실행 큐 소비 실패", "error", err)
		exitCode = 1
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = server.Shutdown(shutdownCtx)
	log.Info("코드 실행기 종료")
	return exitCode
}

// runSelfTest 는 샌드박스 방어를 모두 확인하고 종료 코드를 돌려준다.
//
// 하나가 실패해도 나머지를 계속 돌린다 — 무엇이 뚫려 있는지 한 번에 다 알아야
// 배포 여부를 판단할 수 있다.
func runSelfTest(
	box sandbox.Sandbox,
	registry *runtimes.Registry,
	registryPrefix string,
	log *slog.Logger,
) int {
	probe, err := selftest.ProbeFrom(registry, registryPrefix)
	if err != nil {
		log.Error("검사용 런타임을 찾지 못했습니다", "error", err)
		return 1
	}
	log.Info("샌드박스 방어 검증 시작", "runtime", selftest.ProbeRuntimeID, "image", probe.Image)

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
	defer cancel()

	if selftest.Report(os.Stdout, selftest.Run(ctx, box, probe)) {
		log.Error("샌드박스 방어 검증 실패 — 이 노드에 배포하면 안 됩니다")
		return 1
	}
	log.Info("샌드박스 방어 검증 통과")
	return 0
}

// runVerifyRuntimes 는 정의 파일의 런타임이 이 노드에서 실제로 도는지 확인한다 (#218).
//
// 격리 검사와 나눠 둔 이유는 readiness 패키지 주석에 있다. 이쪽이 실패했다는 것은
// "이 노드는 아직 채점할 준비가 안 됐다" 는 뜻이지, 방어가 뚫렸다는 뜻이 아니다.
func runVerifyRuntimes(
	box sandbox.Sandbox,
	registry *runtimes.Registry,
	registryPrefix string,
	log *slog.Logger,
) int {
	log.Info("런타임 준비 상태 확인 시작", "runtimes", len(registry.All()))

	// 언어 수만큼 컴파일이 돌아간다. 5분으로는 모자란다.
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Minute)
	defer cancel()

	if readiness.Report(os.Stdout, readiness.Check(ctx, box, registry, registryPrefix)) {
		log.Error("런타임 준비 상태 확인 실패 — 이 노드는 그 언어를 채점할 수 없습니다")
		return 1
	}
	log.Info("런타임 준비 상태 확인 통과")
	return 0
}
