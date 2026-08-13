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
    // 배열은 같은 이름으로 여러 번 붙인다 — 서버가 List<String> 으로 받는다 (#232).
    if (Array.isArray(value)) {
      value.filter((it) => it !== "").forEach((it) => url.searchParams.append(key, it));
      return;
    }
    if (value !== undefined && value !== "") url.searchParams.set(key, String(value));
  });

  const headers: Record<string, string> = {};
  /*
    **multipart 는 Content-Type 을 손으로 지정하면 안 된다** (#389).

    경계 문자열이 그 헤더에 들어가는데 그것은 브라우저가 만든다. 지정하면 서버가
    본문을 파싱하지 못한다. 전에는 그래서 파일 업로드만 이 함수를 못 쓰고 손으로
    `fetch` 를 썼는데, **인증 헤더와 주소 규칙이 두 벌**이 됐다.
  */
  const isMultipart = typeof FormData !== "undefined" && body instanceof FormData;
  if (body !== undefined && !isMultipart) headers["Content-Type"] = "application/json";
  if (auth) {
    const token = tokenStore.read();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(url.toString(), {
    method,
    headers,
    body: body === undefined ? undefined : isMultipart ? (body as FormData) : JSON.stringify(body),
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
