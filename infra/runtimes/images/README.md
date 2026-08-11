# 자체 빌드 런타임 이미지

대부분의 언어는 공식 이미지를 그대로 쓴다 (`infra/runtimes/runtimes.yaml` 의 `image`).
여기 있는 것은 **쓸 만한 공식 이미지가 없는 언어**뿐이다.

| 언어 | 이미지 | 왜 직접 만드는가 |
|---|---|---|
| Kotlin | `codekr/runtime-kotlin` | 공식 `kotlinc` 컨테이너 이미지가 없다 |
| C# | `codekr/runtime-csharp` | 단일 `.cs` 를 빌드하려면 프로젝트 골격과 사전 복원이 필요하다 |

## 만들기

```bash
make build-runtimes                              # 엔진 API 저장소로 (로컬 개발)
CODEKR_IMAGE_BUILDER=nerdctl sudo -E \
  bash scripts/build-runtimes.sh                 # containerd 의 codekr 네임스페이스로
```

**빌더를 잘못 고르면 이미지를 못 찾는다.** docker 로 빌드한 이미지는 엔진의 저장소에
들어가는데, containerd 구현은 containerd 의 `codekr` 네임스페이스를 본다 — 빌드는
성공했는데 실행기는 없다고 한다.

CI 는 `infra/runtimes/images/**` 가 바뀔 때만 빌드해 `ghcr.io` 로 올린다
(`.github/workflows/runtime-images.yml`).

## 레지스트리 없이 도는가

**돈다.** 실행기는 이미지가 이미 있으면 레지스트리에 묻지 않는다 (#171). 노드에 미리
받아 두거나 여기서 빌드해 두면, 레지스트리가 비공개이거나 잠깐 흔들려도 채점은 계속된다.

## 새 이미지를 추가할 때

1. `infra/runtimes/images/<언어>/Dockerfile` 을 만든다.
2. `runtimes.yaml` 에 항목을 추가한다.
3. `CODEKR_SANDBOX_TEST=1 go test ./internal/sandbox/ -run TestLiveEveryRegisteredRuntime` 로
   기본 템플릿이 실제로 컴파일·실행되는지 확인한다.

이미지는 다음 제약 아래에서 동작해야 한다 (ADR-0003).

- 네트워크 차단, 읽기 전용 rootfs, 쓰기 가능한 곳은 작업 디렉터리(`/work`)뿐
- non-root(UID 10001) 실행 — 툴체인이 `$HOME` 에 쓰려 하면 `HOME=/work` 로 돌려 둔다
