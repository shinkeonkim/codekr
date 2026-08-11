import { ApiError, request, tokenStore } from "@/shared/api";
import type { TokenResponse, User, UserProfile, UserSettings } from "../model/types";

function authHeader(): Record<string, string> {
  const token = tokenStore.read();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** 인증 관련 서버 호출. 토큰 저장은 features/auth 가 한다. */
export const userApi = {
  signup: (body: { email: string; password: string; nickname: string }) =>
    request<TokenResponse>("/api/v1/auth/signup", { method: "POST", body }),

  login: (body: { email: string; password: string }) =>
    request<TokenResponse>("/api/v1/auth/login", { method: "POST", body }),

  refresh: (refreshToken: string) =>
    request<TokenResponse>("/api/v1/auth/refresh", { method: "POST", body: { refreshToken } }),

  me: () => request<User>("/api/v1/auth/me", { auth: true }),

  /** 회원 프로필 (#83). 로그인 사용자만 볼 수 있다 — 전체 제출 목록과 같은 범위. */
  profile: (nickname: string) =>
    request<UserProfile>(`/api/v1/users/${encodeURIComponent(nickname)}`, { auth: true }),

  settings: () => request<UserSettings>("/api/v1/users/me/settings", { auth: true }),

  /**
   * 아바타 등록·교체 (#116).
   *
   * `request` 는 JSON 만 다루므로 여기서만 fetch 를 직접 쓴다 — multipart 는
   * Content-Type 에 경계 문자열이 들어가서 손으로 지정하면 안 된다.
   */
  uploadAvatar: async (file: File): Promise<{ avatarUrl: string | null }> => {
    const form = new FormData();
    form.append("file", file);

    const response = await fetch("/api/v1/users/me/avatar", {
      method: "PUT",
      headers: authHeader(),
      body: form,
    });
    const payload = await response.json();
    if (!response.ok) {
      throw new ApiError(payload?.code ?? "UNKNOWN", payload?.message ?? "올리지 못했습니다.", response.status);
    }
    return payload;
  },

  removeAvatar: () => request<void>("/api/v1/users/me/avatar", { method: "DELETE", auth: true }),

  /** 보내지 않은 항목은 바뀌지 않는다 (#104). */
  updateSettings: (body: Partial<UserSettings>) =>
    request<UserSettings>("/api/v1/users/me/settings", { method: "PATCH", body, auth: true }),
};
