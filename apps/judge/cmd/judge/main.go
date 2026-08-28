// 코드 채점기 진입점. 채점 큐를 소비해 테스트케이스별로 실행기를 트리거한다.
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
	"github.com/shinkeonkim/codekr/apps/judge/internal/config"
	"github.com/shinkeonkim/codekr/apps/judge/internal/dispatch"
	"github.com/shinkeonkim/codekr/apps/judge/internal/httpapi"
	"github.com/shinkeonkim/codekr/apps/judge/internal/judging"
	"github.com/shinkeonkim/codekr/apps/judge/internal/worker"
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

func main() {
	log := slog.New(contract.NewLogHandler(os.Stdout))
	cfg := config.Load()

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

	log.Info("코드 채점기 시작",
		"redis", cfg.RedisAddr, "http", cfg.HTTPAddr,
		"concurrency", cfg.Concurrency, "execTimeout", cfg.ExecTimeout, "drain", cfg.Drain)

	service := judging.NewService(
		dispatch.NewExecutor(redisClient, cfg.ExecTimeout, cfg.Lane),
		dispatch.NewEventPublisher(redisClient),
		log,
	)
	consumer := worker.NewConsumer(redisClient, service, cfg.ConsumerName, cfg.Concurrency, cfg.Lane, log)
	if err := consumer.Start(ctx, cfg.Drain); err != nil {
		log.Error("채점 큐 소비 실패", "error", err)
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = server.Shutdown(shutdownCtx)
	log.Info("코드 채점기 종료")
}
