# 코드.kr — 로컬 개발 진입점
# 각 앱의 빌드 도구(Gradle/Bun/Go)는 앱 디렉터리 안에서 독립적으로 동작한다.
# 이 Makefile 은 그것들을 감싸는 얇은 진입점일 뿐이다.

COMPOSE := docker compose --env-file .env -f infra/docker/compose.yml
INFRA   := postgres redis
GO_MODULES := apps/executor apps/judge libs/gocontract
# 로컬에 golangci-lint 가 없어도 CI 와 같은 버전으로 검사할 수 있게 컨테이너로 돌린다.
GOLANGCI_IMAGE := golangci/golangci-lint:v2.6-alpine

.DEFAULT_GOAL := help
.PHONY: help env up down clean logs ps infra-up infra-down \
        pull-runtimes build-runtimes verify-runtimes verify-seccomp seed smoke test test-api test-web test-go lint lint-go

help: ## 사용 가능한 명령 표시
	@grep -hE '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

.env:
	@cp .env.example .env && echo "생성됨: .env (.env.example 복사)"

env: .env ## .env 파일 준비

up: env ## 전체 스택 빌드 후 기동
	$(COMPOSE) up -d --build
	@$(MAKE) --no-print-directory ps

down: env ## 컨테이너 중지 및 제거
	$(COMPOSE) down --remove-orphans

clean: env ## 볼륨까지 삭제 (DB 초기화)
	$(COMPOSE) down --remove-orphans --volumes

ps: env ## 컨테이너 상태
	$(COMPOSE) ps

logs: env ## 전체 로그 따라가기
	$(COMPOSE) logs -f --tail=100

logs-%: env ## 특정 서비스 로그 (예: make logs-api)
	$(COMPOSE) logs -f --tail=200 $*

infra-up: env ## postgres, redis 만 기동
	$(COMPOSE) up -d $(INFRA)

infra-down: env ## postgres, redis 만 중지
	$(COMPOSE) stop $(INFRA)

pull-runtimes: ## 코드 실행용 런타임 이미지 미리 받기
	@bash scripts/pull-runtimes.sh

build-runtimes: ## 공식 이미지가 없는 언어의 런타임 이미지 빌드 (kotlin, csharp)
	@bash scripts/build-runtimes.sh

verify-runtimes: ## 등록된 모든 런타임의 기본 템플릿이 실제로 컴파일·실행되는지 확인
	cd apps/executor && CODEKR_SANDBOX_TEST=1 \
		CODEKR_SECCOMP_PROFILE=$(CURDIR)/infra/sandbox/seccomp.json \
		go test ./internal/sandbox/ -run TestLiveEveryRegisteredRuntime -timeout 25m -v

verify-seccomp: ## 좁힌 seccomp 프로파일이 실제로 위험한 syscall 을 막는지 확인 (#48)
	cd apps/executor && CODEKR_SANDBOX_TEST=1 \
		CODEKR_SECCOMP_PROFILE=$(CURDIR)/infra/sandbox/seccomp.json \
		go test ./internal/sandbox/ -run TestLiveNarrowedSeccomp -v

seed: env ## 데모 계정 및 시드 문제 주입
	@bash scripts/seed.sh

smoke: env ## E2E 스모크 테스트 (가입 → 제출 → 판정)
	@bash scripts/smoke.sh

lint: lint-go test-web ## 전체 정적 검사

lint-go: ## golangci-lint 로 Go 모듈 검사
	@for module in $(GO_MODULES); do \
		echo "==> lint $$module"; \
		docker run --rm -v "$(PWD)":/w -w "/w/$$module" $(GOLANGCI_IMAGE) \
			golangci-lint run --config /w/.golangci.yml ./... || exit 1; \
	done

test: test-api test-web test-go ## 전체 단위 테스트

test-api: ## api 단위 테스트
	cd apps/api && ./gradlew test

test-web: ## web 타입 체크 및 린트
	cd apps/web && bun install --frozen-lockfile && bun run typecheck && bun run lint

test-go: ## judge, executor 단위 테스트
	cd apps/executor && go test ./...
	cd apps/judge && go test ./...
