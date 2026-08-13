# 브랜드 자산 (#261)

원본은 이 저장소의 `assets/brand/` 에 있고(백업), 여기 있는 것은 **화면용으로 줄인 사본**이다.
원본은 1254~1536px PNG 라 그대로 쓰면 첫 화면만 수 MB 가 된다.

| 파일 | 출처 | 쓰는 곳 |
|---|---|---|
| `hero.webp` | `hero/hero-image-only.png` | 첫 화면 Hero (비로그인) |
| `character-laptop.webp` | `character/06-laptop.png` | 빈 목록 |
| `character-thinking.webp` | `character/07-thinking.png` | 문제 질문 빈 화면 |
| `character-success.webp` | `character/09-success.png` | 정답 판정 |
| `character-fail.webp` | `character/11-fail.png` | 오답 판정 |
| `character-celebration.webp` | `character/08-celebration.png` | **아직 쓰는 곳이 없다** (아래) |
| `symbol-dark.webp` | `favicon/04-dark-circle.png` | 헤더 심벌 — **라이트 테마** |
| `symbol-light.webp` | `favicon/05-white-circle.png` | 좁은 자리 심벌 — **다크 테마** |
| `state-not-found.webp` | `404.png` | 404 화면 (**장면 전체** — 가짜 버튼이 없다) |
| `state-server-error.webp` | `500.png` | 오류 화면 (그림 속 "TRY AGAIN" 버튼은 잘라 냄) |
| `state-forbidden.webp` | `403.png` | 어드민 접근 거부 (그림 속 "로그인 하기" 버튼은 잘라 냄) |
| `state-welcome.webp` | `welcome.png` | 회원가입 화면 |
| `state-goodbye.webp` | `goodbye.png` | 탈퇴 확인 |
| `state-award.webp` | `award.png` | 랭킹·대회 빈 화면 |
| `state-study.webp` | `study.png` | 문제집 빈 화면 |
| `state-working.webp` | `working-in-progress.png` | 채점 진행 (가로로 납작한 구도) |
| `mascot-cat.webp` | `cat1.png` | 알림 빈 화면 |
| `wordmark.webp` | `logo/codekr-wordmark2.png` | 헤더·푸터 로고 |

두 번째 묶음 (#461):

| 파일 | 출처 | 쓰는 곳 |
|---|---|---|
| `banner-submissions-1.webp` | `banner/submissions-01.png` | 전체 제출 상단 슬라이드쇼 1장 |
| `banner-submissions-2.webp` | `banner/submissions-02.png` | 전체 제출 상단 슬라이드쇼 2장 |
| `banner-admin.webp` | `banner/admin-02.png` | 어드민 첫 화면 배너 |
| `state-login.webp` | `welcome-login.png` | 로그인 화면 |

파비콘·앱 아이콘·OG 이미지는 Next 규약대로 `src/app/` 에 둔다
(`icon.png`, `apple-icon.png`, `opengraph-image.jpg`).
README 그림은 웹이 서빙하지 않으므로 여기 두지 않는다 — `docs/images/readme-welcome.webp` 다.

## 두 번째 묶음에서 달라진 것 (#461)

**배너는 자르지 않았다.** 첫 묶음은 그림 속 버튼("TRY AGAIN", "로그인 하기")을 잘라
냈지만, 배너 셋은 목록 위에 까는 **장식 띠**여서 그림 속 UI 를 조작으로 착각할 자리가
아니다 — 진짜 버튼은 배너 바깥에 따로 있다. 어드민 배너의 숫자(12,543 · 62.4%)도 실제
값이 아니다. **진짜 지표를 그 자리에 넣게 되면 그림을 뺀다.**

**배경 제거도 하지 않았다.** 배너 셋은 배경 자체가 그림이라 걷어 낼 것이 없다.
`state-login.webp` 만 원본이 이미 투명이라 **투명 여백만 잘라 냈다**(1536×1024 →
1154×1014 → 460×404). 첫 묶음의 flood fill 은 이번에 쓸 일이 없었다.

**배너 셋은 폭 1600 이다.** 본문 폭이 `max-w-6xl`(1152px)이라 그 1.4배다. 셋 다 원본
비율 1983×793 을 지켜 1600×640 이 됐다 — **비율이 어긋나면 슬라이드가 바뀔 때 아래
목록이 튄다.**

## 워드마크는 2판을 쓴다

1판(`codekr-wordmark.png`)의 "코드" 는 진한 남색이라 **어두운 배경에서 사라졌다.**
이 사이트는 라이트·다크를 모두 지원하므로 그것으로는 헤더에 걸 수 없었다.

2판(`codekr-wordmark2.png`)은 중간 밝기의 파랑이라 두 배경에서 모두 읽힌다 —
흰 배경과 `#12151b` 에 각각 얹어 확인하고 바꿨다.

**심벌은 헤더에서 함께 쓰지 않는다.** 워드마크의 "드" 안에 이미 `</>` 가 들어 있어,
나란히 놓으면 같은 표시가 두 번 나온다. 심벌은 파비콘과 좁은 자리에 남긴다.

## 원본 11종을 다 쓰지 않는다

앞·옆·뒤 3면(`01`~`04`)은 캐릭터 시트용이지 화면용이 아니다. 화면에는 **상황이 있는
자세**만 쓴다 — 그래야 그림이 그 자리의 뜻을 거든다.

## 자리 점검 (#264)

#264 는 "지금 없어서 다른 그림을 대신 쓰고 있거나 비워 둔 자리" 여덟을 적고 그림을
요청했다. **그 여덟은 전부 들어와 있고 화면에 붙어 있다.** 위 표가 그 자리다.

요청할 때 지켜졌으면 한다고 적은 넷을 실제로 재 봤다.

| 조건 | 결과 |
|---|---|
| 투명 배경 | 14종 전부 알파 채널이 있다 |
| **선언한 크기와 실제 크기가 같은가** | 전부 일치. 어긋나면 그리는 순간 화면이 밀린다 |
| 여백은 최소로 (원본은 40~70%) | **0~21%** — 잘라 낸 것이 반영되어 있다 |
| 어두운 가장자리가 흰 배경에서 테두리처럼 보이는가 | **아니다** (아래) |

### 가장자리는 재 봤을 때와 눈으로 봤을 때가 달랐다

반투명 가장자리 픽셀의 평균 밝기를 재니 `character-*` 다섯과 `mascot-cat` 이 33~57
(255 기준)로 나와, 흰 배경에서 테두리처럼 보일 것처럼 읽혔다.

**흰 배경에 얹어 확대해 보니 아니었다.** 그 어두움은 글로우가 아니라 **캐릭터 자신의
검은 후드와 머리카락이 안티에일리어싱된 것**이다. `state-*` 가 밝게 나온 이유도 같다 —
그쪽 그림은 가장자리에 흰 고양이와 밝은 장식이 많다.

**숫자가 문제를 가리켜도 눈으로 확인하기 전에는 고치지 않는다.**

### `celebration` 만 쓰는 곳이 없다

"처음 맞힌 순간처럼 크게 축하할 때만" 쓰려고 넣었는데, 그런 자리가 아직 없다.

**억지로 넣지 않는다.** #264 가 그림을 요청할 때 세운 규칙 그대로다 — "쓸 곳 없이
그려 두면 어딘가에 억지로 넣게 된다." 첫 해결 축하 같은 기능이 생기면 그때 붙인다.
