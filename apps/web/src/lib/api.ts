import type {
  ActivityResponse,
  AdminProblemDetail,
  Page,
  ProblemDetail,
  ProblemSummary,
  ProblemVerification,
  QueueStatus,
  RunResult,
  Runtime,
  SubmissionDetail,
  SubmissionSummary,
  SubmissionVisibility,
  TokenResponse,
  User,
} from "./types";

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:18080";
export const WS_BASE_URL = process.env.NEXT_PUBLIC_WS_BASE_URL ?? "ws://localhost:18080";

/** 서버가 내려주는 오류 규약을 그대로 감싼 예외. 화면은 message 만 보면 된다. */
export class ApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status: number,
    readonly fieldErrors: { field: string; message: string }[] = [],
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const ACCESS_TOKEN_KEY = "codekr.accessToken";
const REFRESH_TOKEN_KEY = "codekr.refreshToken";

export const tokenStore = {
  read: () => (typeof window === "undefined" ? null : localStorage.getItem(ACCESS_TOKEN_KEY)),
  readRefresh: () => (typeof window === "undefined" ? null : localStorage.getItem(REFRESH_TOKEN_KEY)),
  save(tokens: { accessToken: string; refreshToken: string }) {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  },
  clear() {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};

interface RequestOptions {
  method?: string;
  body?: unknown;
  auth?: boolean;
  query?: Record<string, string | number | undefined>;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, auth = false, query } = options;
  const url = new URL(`${API_BASE_URL}${path}`);
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== "") url.searchParams.set(key, String(value));
  });

  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (auth) {
    const token = tokenStore.read();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(url.toString(), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 204) return undefined as T;

  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new ApiError(
      payload?.code ?? "UNKNOWN",
      payload?.message ?? "요청을 처리하지 못했습니다.",
      response.status,
      payload?.fieldErrors ?? [],
    );
  }
  return payload as T;
}

export const api = {
  signup: (body: { email: string; password: string; nickname: string }) =>
    request<TokenResponse>("/api/v1/auth/signup", { method: "POST", body }),

  login: (body: { email: string; password: string }) =>
    request<TokenResponse>("/api/v1/auth/login", { method: "POST", body }),

  refresh: (refreshToken: string) =>
    request<TokenResponse>("/api/v1/auth/refresh", { method: "POST", body: { refreshToken } }),

  me: () => request<User>("/api/v1/auth/me", { auth: true }),

  problems: (query: Record<string, string | number | undefined>) =>
    request<Page<ProblemSummary>>("/api/v1/problems", { query }),

  problem: (slug: string) => request<ProblemDetail>(`/api/v1/problems/${slug}`),

  runtimes: () => request<Runtime[]>("/api/v1/runtimes"),

  run: (slug: string, body: { runtimeId: string; sourceCode: string; stdin: string }) =>
    request<RunResult>(`/api/v1/problems/${slug}/run`, { method: "POST", body, auth: true }),

  submit: (slug: string, body: { runtimeId: string; sourceCode: string; visibility?: SubmissionVisibility }) =>
    request<{ submissionId: number; status: string }>(`/api/v1/problems/${slug}/submissions`, {
      method: "POST",
      body,
      auth: true,
    }),

  submission: (id: number) => request<SubmissionDetail>(`/api/v1/submissions/${id}`, { auth: true }),

  changeSubmissionVisibility: (id: number, visibility: SubmissionVisibility) =>
    request<void>(`/api/v1/submissions/${id}/visibility`, {
      method: "PATCH",
      body: { visibility },
      auth: true,
    }),

  submissions: (query: Record<string, string | number | undefined>) =>
    request<Page<SubmissionSummary>>("/api/v1/submissions", { auth: true, query }),

  /** 전체 회원의 제출 목록 (#34). 소스 코드는 담기지 않는다. */
  exploreSubmissions: (query: Record<string, string | number | undefined>) =>
    request<Page<SubmissionSummary>>("/api/v1/submissions/explore", { auth: true, query }),

  adminProblems: (query: Record<string, string | number | undefined>) =>
    request<Page<ProblemSummary>>("/api/v1/admin/problems", { auth: true, query }),

  adminProblem: (id: number) => request<AdminProblemDetail>(`/api/v1/admin/problems/${id}`, { auth: true }),

  createProblem: (body: unknown) =>
    request<{ id: number; slug: string }>("/api/v1/admin/problems", { method: "POST", body, auth: true }),

  updateProblem: (id: number, body: unknown) =>
    request<AdminProblemDetail>(`/api/v1/admin/problems/${id}`, { method: "PUT", body, auth: true }),

  verifyProblem: (id: number) =>
    request<ProblemVerification>(`/api/v1/admin/problems/${id}/verify`, { method: "POST", auth: true }),

  deleteProblem: (id: number) =>
    request<void>(`/api/v1/admin/problems/${id}`, { method: "DELETE", auth: true }),

  activity: (query: Record<string, string | number | undefined> = {}) =>
    request<ActivityResponse>("/api/v1/users/me/activity", { auth: true, query }),

  queues: () => request<QueueStatus>("/api/v1/admin/queues", { auth: true }),
};
