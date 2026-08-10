import { request } from "@/shared/api";
import type { TokenResponse, User } from "../model/types";

/** 인증 관련 서버 호출. 토큰 저장은 features/auth 가 한다. */
export const userApi = {
  signup: (body: { email: string; password: string; nickname: string }) =>
    request<TokenResponse>("/api/v1/auth/signup", { method: "POST", body }),

  login: (body: { email: string; password: string }) =>
    request<TokenResponse>("/api/v1/auth/login", { method: "POST", body }),

  refresh: (refreshToken: string) =>
    request<TokenResponse>("/api/v1/auth/refresh", { method: "POST", body: { refreshToken } }),

  me: () => request<User>("/api/v1/auth/me", { auth: true }),
};
