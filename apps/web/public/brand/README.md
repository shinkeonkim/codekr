# 브랜드 자산 (#261)

원본은 이 저장소의 `assets/brand/` 에 있고(백업), 여기 있는 것은 **화면용으로 줄인 사본**이다.
원본은 1254~1536px PNG 라 그대로 쓰면 첫 화면만 수 MB 가 된다.

| 파일 | 출처 | 쓰는 곳 |
|---|---|---|
| `hero.webp` | `hero/hero-image-only.png` | 첫 화면 Hero (비로그인) |
| `character-laptop.webp` | `character/06-laptop.png` | 빈 목록 |
| `character-thinking.webp` | `character/07-thinking.png` | (예약) 채점 중 |
| `character-success.webp` | `character/09-success.png` | 정답 판정 |
| `character-fail.webp` | `character/11-fail.png` | 오답 판정 |
| `character-celebration.webp` | `character/08-celebration.png` | (예약) 축하 |
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

파비콘·앱 아이콘·OG 이미지는 Next 규약대로 `src/app/` 에 둔다
(`icon.png`, `apple-icon.png`, `opengraph-image.jpg`).

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
