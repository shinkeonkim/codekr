// 코드 실행기 진입점. 실행 큐를 소비해 격리 환경에서 코드를 돌린다.
package main

import (
	"context"
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
	"github.com/shinkeonkim/codekr/apps/executor/internal/worker"
)

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	cfg := config.Load()

	registry, err := runtimes.Load(cfg.RuntimesPath)
	if err != nil {
		log.Error("런타임 정의 로드 실패", "error", err)
		os.Exit(1)
	}

	box, err := sandbox.NewContainerSandbox()
	if err != nil {
		log.Error("샌드박스 초기화 실패", "error", err)
		os.Exit(1)
	}
	defer func() { _ = box.Close() }()

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
		"redis", cfg.RedisAddr, "http", cfg.HTTPAddr,
		"concurrency", cfg.Concurrency, "runtimes", registry.Images())

	runner := worker.NewRunner(registry, box, cfg.CompileTimeoutMs, cfg.MaxOutputBytes)
	consumer := worker.NewConsumer(redisClient, runner, cfg.ConsumerName, cfg.Concurrency, log)
	if err := consumer.Start(ctx); err != nil {
		log.Error("실행 큐 소비 실패", "error", err)
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = server.Shutdown(shutdownCtx)
	log.Info("코드 실행기 종료")
}
