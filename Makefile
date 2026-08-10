# 코드.kr — 로컬 개발 진입점
# 각 앱의 빌드 도구(Gradle/Bun/Go)는 앱 디렉터리 안에서 독립적으로 동작한다.
# 이 Makefile 은 그것들을 감싸는 얇은 진입점일 뿐이다.

COMPOSE := docker compose --env-file .env -f infra/docker/compose.yml
INFRA   := postgres redis

.DEFAULT_GOAL := help
.PHONY: help env up down clean logs ps infra-up infra-down \
        pull-runtimes seed smoke test test-api test-web test-go

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

seed: env ## 데모 계정 및 시드 문제 주입
	@bash scripts/seed.sh

smoke: env ## E2E 스모크 테스트 (가입 → 제출 → 판정)
	@bash scripts/smoke.sh

test: test-api test-web test-go ## 전체 단위 테스트

test-api: ## api 단위 테스트
	cd apps/api && ./gradlew test

test-web: ## web 타입 체크 및 린트
	cd apps/web && bun install --frozen-lockfile && bun run typecheck && bun run lint

test-go: ## judge, executor 단위 테스트
	cd apps/executor && go test ./...
	cd apps/judge && go test ./...
