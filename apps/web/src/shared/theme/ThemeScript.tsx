/**
 * 첫 그림 전에 테마를 바르는 인라인 스크립트 (#206).
 *
 * **번들이 아니라 문자열이다.** 번들은 HTML 이 그려진 뒤에 받아 실행되므로, 거기서
 * 바르면 서버가 보낸 밝은 화면이 한 번 보였다가 어두워진다 — 이슈가 "가장 까다로운
 * 부분" 이라고 적은 그 번쩍임이다. `<head>` 안의 동기 스크립트만 그것을 앞지른다.
 *
 * 그래서 저장 키를 상수로 가져다 쓰지 못하고 **글자로 적는다.** `theme.ts` 의
 * `THEME_STORAGE_KEY` 와 같아야 한다.
 *
 * `system` 이면 아무것도 하지 않는다 — CSS 의 `color-scheme: light dark` 가 이미
 * OS 를 따르고 있어서, 여기서 손대면 오히려 한 번 더 계산할 뿐이다.
 */
const SCRIPT = `
try {
  var t = localStorage.getItem("codekr:theme");
  if (t === "light" || t === "dark") document.documentElement.setAttribute("data-theme", t);
} catch (e) {}
`;

export function ThemeScript() {
  // biome-ignore lint/security/noDangerouslySetInnerHtml: 첫 그림을 앞지르려면 인라인이어야 한다.
  return <script dangerouslySetInnerHTML={{ __html: SCRIPT }} />;
}
