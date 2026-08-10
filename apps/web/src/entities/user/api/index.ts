import { request } from "@/shared/api";
import type { TokenResponse, User, UserProfile, UserSettings } from "../model/types";

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

  /** 보내지 않은 항목은 바뀌지 않는다 (#104). */
  updateSettings: (body: Partial<UserSettings>) =>
    request<UserSettings>("/api/v1/users/me/settings", { method: "PATCH", body, auth: true }),
};
