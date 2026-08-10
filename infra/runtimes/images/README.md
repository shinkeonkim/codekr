# 자체 빌드 런타임 이미지

대부분의 언어는 공식 이미지를 그대로 쓴다 (`infra/runtimes/runtimes.yaml` 의 `image`).
여기 있는 것은 **쓸 만한 공식 이미지가 없는 언어**뿐이다.

| 언어 | 이미지 | 왜 직접 만드는가 |
|---|---|---|
| Kotlin | `codekr/runtime-kotlin` | 공식 `kotlinc` 컨테이너 이미지가 없다 |
| C# | `codekr/runtime-csharp` | 단일 `.cs` 를 빌드하려면 프로젝트 골격과 사전 복원이 필요하다 |

## 만들기

```bash
make build-runtimes      # 로컬 빌드
```

CI 는 `infra/runtimes/images/**` 가 바뀔 때만 빌드해 `ghcr.io` 로 올린다
(`.github/workflows/runtime-images.yml`).

## 새 이미지를 추가할 때

1. `infra/runtimes/images/<언어>/Dockerfile` 을 만든다.
2. `runtimes.yaml` 에 항목을 추가한다.
3. `CODEKR_SANDBOX_TEST=1 go test ./internal/sandbox/ -run TestLiveEveryRegisteredRuntime` 로
   기본 템플릿이 실제로 컴파일·실행되는지 확인한다.

이미지는 다음 제약 아래에서 동작해야 한다 (ADR-0003).

- 네트워크 차단, 읽기 전용 rootfs, 쓰기 가능한 곳은 작업 디렉터리(`/work`)뿐
- non-root(UID 10001) 실행 — 툴체인이 `$HOME` 에 쓰려 하면 `HOME=/work` 로 돌려 둔다
