// Package httpapi 는 상태 점검과 메트릭 엔드포인트만 제공한다.
// 실행 요청은 HTTP 가 아니라 큐로만 받는다 (ADR-0002).
package httpapi

import (
	"context"
	"net/http"
	"time"

	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/redis/go-redis/v9"
)

// NewServer 는 /healthz 와 /metrics 를 제공하는 HTTP 서버를 만든다.
func NewServer(addr string, client *redis.Client) *http.Server {
	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", healthHandler(client))
	mux.Handle("/metrics", promhttp.Handler())

	return &http.Server{
		Addr:              addr,
		Handler:           mux,
		ReadHeaderTimeout: 5 * time.Second,
	}
}

// healthHandler 는 Redis 연결까지 확인한다 — 큐에 붙지 못하면 이 워커는 일을 못 한다.
func healthHandler(client *redis.Client) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
		defer cancel()

		if err := client.Ping(ctx).Err(); err != nil {
			w.WriteHeader(http.StatusServiceUnavailable)
			_, _ = w.Write([]byte(`{"status":"DOWN","reason":"redis"}`))
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"status":"UP"}`))
	}
}
