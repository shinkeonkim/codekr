/**
 * 서버 주소.
 *
 * **기본은 같은 출처(same-origin)다.** 배포에서는 Ingress 가 `/api` 와 `/ws` 를 api 로,
 * 나머지를 web 으로 보내므로 브라우저는 자기가 열린 주소를 그대로 쓰면 된다.
 *
 * 왜 주소를 이미지에 박지 않는가: `NEXT_PUBLIC_*` 은 **빌드 시점에 번들로 들어간다.**
 * 주소를 박으면 환경마다 이미지를 따로 빌드해야 하고, 같은 이미지를 공개 주소(코드.kr)와
 * 내부 주소로 함께 서비스할 수 없다. 하나를 박으면 다른 쪽에서 CORS 로 막힌다.
 *
 * 로컬 개발은 web(:13000)과 api(:18080)의 출처가 달라서 환경 변수로 지정한다.
 */
const CONFIGURED_API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
const CONFIGURED_WS_BASE_URL = process.env.NEXT_PUBLIC_WS_BASE_URL ?? "";

/**
 * API 요청의 접두사. 빈 문자열이면 상대 경로가 되어 같은 출처로 나간다.
 *
 * 서버 렌더링 중에는 브라우저 주소를 알 수 없다. 다만 현재 모든 API 호출은 클라이언트
 * 컴포넌트에서 일어나므로 문제가 되지 않는다 — 서버에서 호출해야 할 일이 생기면
 * 그때는 클러스터 내부 주소를 따로 받아야 한다.
 */
export const API_BASE_URL = CONFIGURED_API_BASE_URL;

/**
 * WebSocket 주소. 지정되지 않았으면 **지금 열린 페이지의 출처**에서 만든다.
 *
 * https 로 열렸으면 wss 여야 한다 — ws 로 붙으면 브라우저가 혼합 콘텐츠로 막는다.
 */
export function wsBaseUrl(): string {
  if (CONFIGURED_WS_BASE_URL) return CONFIGURED_WS_BASE_URL;
  if (typeof window === "undefined") return "";

  const scheme = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${scheme}//${window.location.host}`;
}
