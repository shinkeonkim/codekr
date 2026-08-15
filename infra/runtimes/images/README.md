# 자체 빌드 런타임 이미지

대부분의 언어는 공식 이미지를 그대로 쓴다 (`infra/runtimes/runtimes.yaml` 의 `image`).
여기 있는 것은 **쓸 만한 공식 이미지가 없는 언어**뿐이다.

| 언어 | 이미지 | 왜 직접 만드는가 |
|---|---|---|
| Kotlin | `codekr-runtime-kotlin` | 공식 `kotlinc` 컨테이너 이미지가 없다 |
| C# | `codekr-runtime-csharp` | 단일 `.cs` 를 빌드하려면 프로젝트 골격과 사전 복원이 필요하다 |
| 아희 | `codekr-runtime-aheui` | 난해한 언어라 공식 이미지가 없다 (#394) |
| 엄랭 | `codekr-runtime-umjunsik` | 같은 이유 (#394) |

## 난해한 언어를 고른 기준 (#394)

**구현이 여럿인 언어는 버전을 숫자로 고정할 수 있는 것을 정본으로 삼는다.**

| 언어 | 정본 | 고정 방법 | 왜 그것인가 |
|---|---|---|---|
| 아희 | `pyaheui` | `pip install aheui==1.2.5` | PyPI 에 있어 버전이 숫자다. 소스 빌드 구현은 빌드 도구까지 고정해야 한다 |
| 엄랭 | `rycont/umjunsik-lang` 의 파이썬 구현 | **커밋 sha** | 그 저장소에 릴리스 태그가 없다 — 브랜치를 쓰면 어느 날 다른 것이 노드에 들어간다 (#96 과 같은 이유) |

**둘 다 JIT 없는 순수 파이썬이라 느리다.** 지금 시드 문제 규모에서는 39ms·29ms 로
돌지만, 무거운 문제가 오면 런타임별 제한(#97)으로 푼다.

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
4. **운영 레지스트리에 올린다.** `oh-my-homelab` 의
   `scripts/k8s/codekr_런타임_이미지_빌드.sh` 를 돌린다 — `images/` 를 전부 도므로
   새로 넣은 것도 함께 만들어진다.
5. 올라갔는지 확인한다.

   ```
   CODEKR_RUNTIME_REGISTRY=registry.shinkeonkim.com scripts/check-runtime-images.sh
   ```

> ⚠️ **4번을 빠뜨려도 아무 데서도 티가 안 난다.** 로컬은 `make build-runtimes` 로 만든
> 것이 노드에 있어 잘 돌고, CI 는 런타임 매트릭스를 뺀다 — **운영에서 그 언어로 처음
> 채점할 때** 실행기가 404 를 받고 `SYSTEM_ERROR` 로 끝난다.
>
> 실제로 아희·엄랭이 그렇게 비어 있었다 (#394 → #588). 5번이 그것을 잡는다.

이미지는 다음 제약 아래에서 동작해야 한다 (ADR-0003).

- 네트워크 차단, 읽기 전용 rootfs, 쓰기 가능한 곳은 작업 디렉터리(`/work`)뿐
- non-root(UID 10001) 실행 — 툴체인이 `$HOME` 에 쓰려 하면 `HOME=/work` 로 돌려 둔다
