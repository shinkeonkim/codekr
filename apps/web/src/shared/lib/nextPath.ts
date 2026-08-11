/**
 * 로그인 후 돌아갈 경로를 안전하게 다룬다 (#113).
 *
 * **같은 출처의 경로만 받는다.** 외부 URL 을 그대로 쓰면 오픈 리다이렉트가 된다 —
 * 공격자가 `?next=https://evil.example` 링크를 뿌려 로그인 직후 남의 사이트로 보낼 수 있다.
 */
const FALLBACK = "/problems";

export function safeNextPath(raw: string | null | undefined): string {
  if (!raw) return FALLBACK;

  // 스킴이나 프로토콜 상대 주소(//host)는 외부로 나갈 수 있다.
  if (!raw.startsWith("/") || raw.startsWith("//")) return FALLBACK;

  // 역슬래시를 슬래시로 해석하는 브라우저가 있어 \\evil.example 도 막는다.
  if (raw.includes("\\")) return FALLBACK;

  return raw;
}

/** 지금 경로(쿼리 포함)를 next 파라미터로 만든다. */
export function toNextParam(pathname: string, search: string): string {
  return encodeURIComponent(`${pathname}${search}`);
}

/**
 * 지금 열린 주소를 next 파라미터로 만든다.
 *
 * `useSearchParams()` 를 쓰지 않는 이유: 그 훅은 페이지 전체를 정적 프리렌더에서
 * 빼내고 Suspense 경계를 요구한다. 이 값이 필요한 시점은 언제나 클라이언트라
 * `window.location` 을 직접 읽는 편이 단순하다.
 */
export function currentNextParam(): string {
  if (typeof window === "undefined") return "";
  return encodeURIComponent(`${window.location.pathname}${window.location.search}`);
}

/** 주소창의 next 파라미터를 안전하게 읽는다. */
export function readNextParam(): string {
  if (typeof window === "undefined") return FALLBACK;
  return safeNextPath(new URLSearchParams(window.location.search).get("next"));
}
