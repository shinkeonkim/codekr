import { API_BASE_URL } from "../config";
import { ApiError } from "./error";
import { tokenStore } from "./tokenStore";
import type { Query } from "./types";

interface RequestOptions {
  method?: string;
  body?: unknown;
  auth?: boolean;
  query?: Query;
}

/**
 * 모든 서버 호출이 지나는 한 곳.
 *
 * 도메인을 전혀 모른다 — 어떤 엔드포인트가 있는지는 각 entity/feature 가 안다.
 * 그래야 새 도메인이 생겨도 이 파일은 그대로다.
 */
/** 서버 렌더링 중에는 window 가 없다. 그 경우는 절대 주소 설정이 반드시 있어야 한다. */
function resolveOrigin(): string | undefined {
  return typeof window === "undefined" ? undefined : window.location.origin;
}

/**
 * 경로를 실제로 부를 주소로 만든다.
 *
 * **JSON 이 아닌 요청(multipart)도 이것을 써야 한다.** 상대 경로로 그냥 `fetch` 하면
 * API 가 다른 출처에 있을 때 웹 서버로 가서 404 가 된다 — 아바타 업로드가 실제로
 * 그랬다 (#115 검수).
 *
 * `API_BASE_URL` 이 비어 있으면 같은 출처다. URL 은 절대 주소를 요구하므로 그때는
 * 현재 출처를 기준으로 만든다.
 */
export function apiUrl(path: string): URL {
  return new URL(`${API_BASE_URL}${path}`, resolveOrigin());
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, auth = false, query } = options;
  const url = apiUrl(path);
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
