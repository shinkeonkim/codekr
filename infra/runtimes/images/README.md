# 자체 빌드 런타임 이미지

대부분의 언어는 공식 이미지를 그대로 쓴다 (`infra/runtimes/runtimes.yaml` 의 `image`).
여기 있는 것은 **쓸 만한 공식 이미지가 없는 언어**뿐이다.

| 언어 | 이미지 | 왜 직접 만드는가 |
|---|---|---|
| Kotlin | `codekr-runtime-kotlin` | 공식 `kotlinc` 컨테이너 이미지가 없다 |
| C# | `codekr-runtime-csharp` | 단일 `.cs` 를 빌드하려면 프로젝트 골격과 사전 복원이 필요하다 |

**주소에 레지스트리를 적지 않는다.** 레지스트리는 실행기 설정(`CODEKR_RUNTIME_REGISTRY`)이
앞에 붙인다 — 공개 이미지와 같은 규칙이다.

## 만들기

```bash
make build-runtimes                              # 엔진 API 저장소로 (로컬 개발)
CODEKR_IMAGE_BUILDER=nerdctl sudo -E \
  bash scripts/build-runtimes.sh                 # containerd 의 codekr 네임스페이스로
```

**빌더를 잘못 고르면 이미지를 못 찾는다.** docker 로 빌드한 이미지는 엔진의 저장소에
들어가는데, containerd 구현은 containerd 의 `codekr` 네임스페이스를 본다 — 빌드는
성공했는데 실행기는 없다고 한다.

## 운영에서는 어디서 만드는가 — 클러스터 안

**GitHub Actions 로 만들어 ghcr 에 올리는 방식을 그만뒀다** (#171).

- 실행 노드가 amd64 인데 손으로 만들면 만든 사람의 아키텍처가 따라온다
- 이미지를 밖에 두면 받아올 자격증명과 공개 여부를 또 관리해야 한다
- 무엇보다, 만든 것을 **쓰는 곳과 같은 클러스터에서** 만드는 편이 단순하다

지금은 홈랩 클러스터 안에서 buildkit Job 이 만들어 자체 레지스트리에 넣는다.
매니페스트와 절차는 `oh-my-homelab` 저장소에 있다.

공개 런타임 13종은 레지스트리가 업스트림에서 직접 당겨온다(zot sync) — 만들 것이 없다.

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
