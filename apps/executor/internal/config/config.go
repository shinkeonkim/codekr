// Package config 는 환경 변수에서 실행기 설정을 읽는다.
package config

import (
	"os"
	"strconv"
	"time"
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
	// SeccompProfilePath 는 샌드박스 컨테이너에 걸 seccomp 프로파일 파일이다 (#48).
	// 비어 있으면 런타임 기본 프로파일을 쓴다.
	SeccompProfilePath string
	// RuntimeRegistry 는 런타임 이미지를 받아올 레지스트리다 (#96).
	// 비면 정의 파일의 주소 그대로 — 로컬 개발은 원본에서 받는다.
	RuntimeRegistry string

	// SelfTestOnStart 는 서비스를 시작하기 전에 샌드박스 방어를 확인할지다 (#246).
	//
	// **배포된 노드에서 통과하는 것이 실제로 중요한 쪽이다** (docs/07). 자동 배포가
	// 붙으면 사람이 `--self-test` 를 돌려 보는 단계가 사라지므로, 그 확인을 기동에
	// 붙여 통과하지 못한 실행기가 채점을 받지 않게 한다.
	SelfTestOnStart bool

	// Drain 은 종료 신호를 받은 뒤 **하던 실행을 마치는 데 주는 시간**이다 (#415).
	// 파드의 `terminationGracePeriodSeconds` 보다 짧아야 한다.
	Drain time.Duration
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
		SeccompProfilePath:   env("CODEKR_SECCOMP_PROFILE", ""),
		RuntimeRegistry:      env("CODEKR_RUNTIME_REGISTRY", ""),
		SelfTestOnStart:      env("EXECUTOR_SELF_TEST_ON_START", "false") == "true",
		Drain:                time.Duration(envInt("CODEKR_DRAIN_SECONDS", 90)) * time.Second,
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
