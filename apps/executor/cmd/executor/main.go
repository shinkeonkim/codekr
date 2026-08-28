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
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
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

	log := slog.New(contract.NewLogHandler(os.Stdout))
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

	go warmImages(ctx, box, registry.Images(), cfg.RuntimeRegistry, log)

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
	// 검사마다 런타임이 다를 수 있다 (#456) — 셸 검사는 셸 이미지로 돌아야 뜻이 있다.
	probes, err := selftest.Probes(registry, registryPrefix)
	if err != nil {
		log.Error("검사용 런타임을 찾지 못했습니다", "error", err)
		return 1
	}
	for id, probe := range probes {
		log.Info("샌드박스 방어 검증 시작", "runtime", id, "image", probe.Image)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
	defer cancel()

	if selftest.Report(os.Stdout, selftest.Run(ctx, box, probes)) {
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

/*
warmImages 는 정의 파일의 이미지를 미리 받아 둔다 (#712).

**기동을 막지 않는다.** 열아홉 개를 다 받고 뜨게 하면 배포가 몇 분씩 걸리고, 그동안
큐가 쌓인다. 배경으로 돌리면 파드는 바로 일을 시작하고 이미지는 곧 따뜻해진다.

**한 번에 하나씩 받는다.** 동시에 당기면 노드의 디스크와 네트워크를 실행 중인 채점과
나눠 쓰게 된다 — 미리 받기가 지금 도는 채점을 느리게 만들면 안 된다.

실패해도 계속한다. 하나가 안 받아진다고 나머지를 포기할 이유가 없고, 실패한 것은
전처럼 첫 제출이 기다릴 뿐이다.
*/
func warmImages(ctx context.Context, box sandbox.Sandbox, images []string, prefix string, log *slog.Logger) {
	started := time.Now()
	warmed, failed := 0, 0
	for _, image := range images {
		if ctx.Err() != nil {
			return
		}
		ref := image
		if prefix != "" {
			ref = prefix + "/" + image
		}
		if err := box.Warm(ctx, ref); err != nil {
			failed++
			// **경고로 남긴다.** 채점은 그대로 돌므로 오류가 아니다. 다만 그 런타임의
			// 첫 제출은 여전히 이미지 받기를 기다린다.
			log.Warn("런타임 이미지를 미리 받지 못했습니다", "image", ref, "error", err)
			continue
		}
		warmed++
	}
	log.Info("런타임 이미지 준비 완료",
		"받음", warmed, "실패", failed, "걸린시간", time.Since(started).Round(time.Second).String())
}
