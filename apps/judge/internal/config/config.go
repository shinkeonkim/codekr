// Package config 는 환경 변수에서 채점기 설정을 읽는다.
package config

import (
	"os"
	"strconv"
	"time"
)

// Config 는 채점기 구동에 필요한 모든 설정이다.
type Config struct {
	RedisAddr    string
	HTTPAddr     string
	Concurrency  int
	ExecTimeout  time.Duration
	ConsumerName string
	// Lane 은 이 워커가 설 채점 차선이다 (#62). general | contest.
	Lane string
	/*
		Drain 은 종료 신호를 받은 뒤 **하던 채점을 마치는 데 주는 시간**이다 (#415).

		파드의 `terminationGracePeriodSeconds` 보다 짧아야 한다 — 길게 잡아도 그쪽이
		먼저 SIGKILL 을 보내므로 의미가 없고, 오히려 배포가 그만큼 느려진다.
	*/
	Drain time.Duration
}

// Load 는 환경 변수를 읽어 설정을 만든다.
func Load() Config {
	hostname, _ := os.Hostname()
	if hostname == "" {
		hostname = "judge"
	}
	return Config{
		RedisAddr:    env("CODEKR_REDIS_ADDR", "localhost:16379"),
		HTTPAddr:     env("CODEKR_HTTP_ADDR", ":8082"),
		Concurrency:  envInt("JUDGE_CONCURRENCY", 4),
		ExecTimeout:  time.Duration(envInt("JUDGE_EXEC_TIMEOUT_MS", 60000)) * time.Millisecond,
		ConsumerName: env("CODEKR_CONSUMER_NAME", hostname),
		Lane:         env("CODEKR_JUDGE_LANE", "general"),
		Drain:        time.Duration(envInt("CODEKR_DRAIN_SECONDS", 90)) * time.Second,
	}
}

func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func envInt(key string, fallback int) int {
	if value, err := strconv.Atoi(os.Getenv(key)); err == nil && value > 0 {
		return value
	}
	return fallback
}
