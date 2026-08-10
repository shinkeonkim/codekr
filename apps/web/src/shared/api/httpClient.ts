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
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
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
