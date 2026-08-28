# 차트가 담아 나르는 파일

**모두 심볼릭 링크다.** 사본을 두지 않는다 — 한때 사본이었고 실제로 갈라졌다.
이미지 이름을 짧은 형태로 바꾼 뒤(#171) 차트 쪽 사본만 옛 `ghcr.io/...` 주소를 들고
있었고, 배포된 실행기가 존재하지 않는 이미지를 받으려 했다.

| 링크 | 원본 | 쓰는 곳 |
|---|---|---|
| `runtimes.yaml` | `infra/runtimes/runtimes.yaml` | 실행기·api 가 읽는 런타임 정의 |
| `seccomp.json` | `infra/sandbox/seccomp.json` | 샌드박스 seccomp 프로파일 (#48) |
| `litellm-config.yaml` | `infra/litellm/config.yaml` | 모델 프록시가 여는 이름과 폴백 (#648, #649) |

Helm 은 차트 디렉터리 밖을 가리키는 링크도 따라간다. 클론한 저장소에서도 같다 —
`helm template` 으로 확인했다.
