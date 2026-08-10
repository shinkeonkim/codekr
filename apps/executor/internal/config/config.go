// Package config 는 환경 변수에서 실행기 설정을 읽는다.
package config

import (
	"os"
	"strconv"
)

// Config 는 실행기 구동에 필요한 모든 설정이다.
type Config struct {
	RedisAddr        string
	HTTPAddr         string
	RuntimesPath     string
	Concurrency      int
	CompileTimeoutMs int
	// 컴파일 단계에 허용할 메모리. 툴체인은 사용자 프로그램보다 훨씬 많이 쓴다.
	CompileMemoryLimitMb int
	MaxOutputBytes       int
	// ConsumerName 은 Redis consumer group 안에서 이 인스턴스를 식별한다.
	ConsumerName string
	// SandboxRuntime 은 어떤 컨테이너 런타임 구현을 쓸지 고른다 (#45).
	SandboxRuntime string
}

// Load 는 환경 변수를 읽어 설정을 만든다. 값이 없으면 로컬 개발에 맞는 기본값을 쓴다.
func Load() Config {
	hostname, _ := os.Hostname()
	if hostname == "" {
		hostname = "executor"
	}
	return Config{
		RedisAddr:            env("CODEKR_REDIS_ADDR", "localhost:16379"),
		HTTPAddr:             env("CODEKR_HTTP_ADDR", ":8081"),
		RuntimesPath:         env("CODEKR_RUNTIMES_PATH", "/etc/codekr/runtimes.yaml"),
		Concurrency:          envInt("EXECUTOR_CONCURRENCY", 4),
		CompileTimeoutMs:     envInt("EXECUTOR_COMPILE_TIMEOUT_MS", 15000),
		CompileMemoryLimitMb: envInt("EXECUTOR_COMPILE_MEMORY_MB", 1024),
		MaxOutputBytes:       envInt("EXECUTOR_MAX_OUTPUT_BYTES", 65536),
		ConsumerName:         env("CODEKR_CONSUMER_NAME", hostname),
		SandboxRuntime:       env("CODEKR_SANDBOX_RUNTIME", "engine-api"),
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
