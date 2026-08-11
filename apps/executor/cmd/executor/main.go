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

	// 자체 검증은 큐도 런타임 정의도 필요 없다. 노드에 실행기 이미지만 있으면 돌아간다.
	if *selfTest {
		return runSelfTest(box, log)
	}

	registry, err := runtimes.Load(cfg.RuntimesPath)
	if err != nil {
		log.Error("런타임 정의 로드 실패", "error", err)
		return 1
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

	runner := worker.NewRunner(registry, box, cfg.CompileTimeoutMs, cfg.CompileMemoryLimitMb, cfg.MaxOutputBytes)
	consumer := worker.NewConsumer(redisClient, runner, cfg.ConsumerName, cfg.Concurrency, log)
	exitCode := 0
	if err := consumer.Start(ctx); err != nil {
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
func runSelfTest(box sandbox.Sandbox, log *slog.Logger) int {
	log.Info("샌드박스 방어 검증 시작", "image", selftest.ProbeImage)

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
	defer cancel()

	if selftest.Report(os.Stdout, selftest.Run(ctx, box)) {
		log.Error("샌드박스 방어 검증 실패 — 이 노드에 배포하면 안 됩니다")
		return 1
	}
	log.Info("샌드박스 방어 검증 통과")
	return 0
}
