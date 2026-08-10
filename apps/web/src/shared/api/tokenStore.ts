const ACCESS_TOKEN_KEY = "codekr.accessToken";
const REFRESH_TOKEN_KEY = "codekr.refreshToken";

/**
 * 토큰 보관소. 서버 렌더링 중에는 localStorage 가 없으므로 항상 null 을 돌려준다 —
 * 호출부가 매번 window 를 확인하지 않아도 되게 여기서 한 번만 다룬다.
 */
export const tokenStore = {
  read: () => (typeof window === "undefined" ? null : localStorage.getItem(ACCESS_TOKEN_KEY)),
  readRefresh: () =>
    typeof window === "undefined" ? null : localStorage.getItem(REFRESH_TOKEN_KEY),
  save(tokens: { accessToken: string; refreshToken: string }) {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  },
  clear() {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};
