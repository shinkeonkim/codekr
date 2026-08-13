import { ApiError, apiUrl, request, tokenStore } from "@/shared/api";
import type { Page } from "@/shared/api";
import type {
  AdminAuditLog,
  AdminUserDetail,
  AdminUserSummary,
  Suspension,
  TokenResponse,
  User,
  UserProfile,
  UserSettings,
} from "../model/types";

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

    // **`apiUrl` 을 거친다.** 상대 경로로 부르면 API 가 다른 출처일 때 웹 서버로 가서
    // 404 가 된다 — 실제로 그랬다.
    const response = await fetch(apiUrl("/api/v1/users/me/avatar").toString(), {
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

  /** 회원 탈퇴 (#140). **되돌릴 수 없다** — 유예 기간을 두지 않았다. */
  withdraw: () => request<void>("/api/v1/users/me", { method: "DELETE", auth: true }),

  /** 보내지 않은 항목은 바뀌지 않는다 (#104). */
  /** 어드민 회원 검색 (#223). 찾는 것은 ADMIN 까지 열린다. */
  adminSearch: (query: {
    q?: string;
    role?: string;
    // Query 는 문자열만 받는다 — 불리언을 넣으면 타입이 어긋난다.
    includeWithdrawn?: string;
    page?: number;
    size?: number;
  }) => request<Page<AdminUserSummary>>("/api/v1/admin/users", { auth: true, query }),

  adminDetail: (id: number) =>
    request<AdminUserDetail>(`/api/v1/admin/users/${id}`, { auth: true }),

  /** 역할 변경 (#103). **최고 관리자만** — 서버가 막는다. */
  replaceRoles: (id: number, roles: string[]) =>
    request<string[]>(`/api/v1/admin/users/${id}/roles`, { method: "PUT", body: { roles }, auth: true }),

  /**
   * 강제 탈퇴 (#140). 되돌릴 수 없다 — 이메일·닉네임이 그 자리에서 지워진다.
   *
   * **사유가 필수다** (#225). 계정이 사라진 뒤에 "누가 왜" 를 물으면 그것이 유일한 답이다.
   */
  forceWithdraw: (id: number, reason: string) =>
    request<void>(`/api/v1/admin/users/${id}`, { method: "DELETE", auth: true, query: { reason } }),

  /**
   * 재설정 메일 요청 (#315).
   *
   * **가입 여부와 무관하게 성공으로 온다.** 다르게 답하면 어느 주소가 가입되어 있는지
   * 확인하는 도구가 된다 — 화면 문구도 그에 맞춰야 거짓말이 되지 않는다.
   */
  requestPasswordReset: (email: string) =>
    request<void>("/api/v1/auth/password/reset-requests", { method: "POST", body: { email } }),

  /** 새 비밀번호 정하기 (#315). */
  resetPassword: (token: string, newPassword: string) =>
    request<void>("/api/v1/auth/password/reset", { method: "POST", body: { token, newPassword } }),

  /** 인증 링크를 확인한다 (#233). **로그인이 필요 없다** — 토큰 자체가 본인 확인이다. */
  verifyEmail: (token: string) =>
    request<void>("/api/v1/auth/email/verify", { method: "POST", body: { token } }),

  /** 인증 메일을 다시 받는다. 쿨다운과 하루 상한이 걸린다. */
  resendVerification: () =>
    request<void>("/api/v1/auth/email/verification", { method: "POST", auth: true }),

  /** 내 프로필에서 남에게 보이는 값 (#310). 설정(내가 보는 값)과 나눈다. */
  updateProfile: (body: { bio?: string }) =>
    request<{ bio: string | null }>("/api/v1/users/me/profile", { method: "PATCH", auth: true, body }),

  /** 어드민이 소개를 지운다 (#310). 신고 기능이 없어도 지울 길은 있어야 한다. */
  clearBio: (id: number, reason: string) =>
    request<void>(`/api/v1/admin/users/${id}/bio`, { method: "DELETE", auth: true, query: { reason } }),

  /**
   * 회원 정지 (#224). **`ADMIN` 이 건다** — 되돌릴 수 있는 조치라, 실제로 게시판을
   * 지키는 사람이 쓸 수 있어야 한다.
   */
  suspend: (id: number, body: { scope: string; reason: string; days: number | null }) =>
    request<Suspension>(`/api/v1/admin/users/${id}/suspensions`, { method: "POST", auth: true, body }),

  /** 지금 걸려 있는 정지들. 기한이 지난 것은 서버가 이미 뺀다. */
  activeSuspensions: (id: number) =>
    request<Suspension[]>(`/api/v1/admin/users/${id}/suspensions`, { auth: true }),

  /** 기한 전에 푼다. 기한이 지난 것은 저절로 풀리므로 여기에 오지 않는다. */
  liftSuspension: (id: number, suspensionId: number) =>
    request<void>(`/api/v1/admin/users/${id}/suspensions/${suspensionId}`, { method: "DELETE", auth: true }),

  /** 관리 기록 (#225). **최고 관리자만** — 어드민끼리 서로를 보는 것이다. */
  auditLogs: (query: { targetUserId?: number; actorId?: number; page?: number; size?: number }) =>
    request<Page<AdminAuditLog>>("/api/v1/admin/audit-logs", { auth: true, query }),

  updateSettings: (body: Partial<UserSettings>) =>
    request<UserSettings>("/api/v1/users/me/settings", { method: "PATCH", body, auth: true }),
};
