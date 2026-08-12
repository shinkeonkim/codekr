# 브랜드 자산 (#261)

원본은 별도 저장소(`codekr-asset`)에 있고, 여기 있는 것은 **화면용으로 줄인 사본**이다.
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
| `symbol-light.webp` | `favicon/05-white-circle.png` | 헤더 심벌 — **다크 테마** |
| `wordmark.webp` | `logo/codekr-wordmark.png` | (아직 안 씀 — 아래) |

파비콘·앱 아이콘·OG 이미지는 Next 규약대로 `src/app/` 에 둔다
(`icon.png`, `apple-icon.png`, `opengraph-image.jpg`).

## 워드마크를 아직 쓰지 않는 이유

`wordmark.webp` 의 "코드" 는 진한 남색이라 **어두운 배경에서 읽히지 않는다.**
이 사이트는 라이트·다크를 모두 지원하므로, 밝은 배경용 워드마크가 생기기 전까지
헤더는 심벌 + 글자로 둔다.

## 원본 11종을 다 쓰지 않는다

앞·옆·뒤 3면(`01`~`04`)은 캐릭터 시트용이지 화면용이 아니다. 화면에는 **상황이 있는
자세**만 쓴다 — 그래야 그림이 그 자리의 뜻을 거든다.
