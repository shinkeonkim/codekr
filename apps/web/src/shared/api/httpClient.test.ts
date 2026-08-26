import { afterEach, describe, expect, test } from "bun:test";
import { ApiError } from "./error";
import { apiUrl, request } from "./httpClient";
import { tokenStore } from "./tokenStore";

/**
 * 모든 서버 호출이 지나는 한 곳 (#646).
 *
 * **여기가 0% 였다.** 도메인을 모르는 파일이라 화면 시험으로는 닿지 않고, 그런데도
 * 모든 요청이 지난다 — 질의 문자열을 만드는 규칙, 인증 헤더, multipart 예외,
 * 오류를 `ApiError` 로 바꾸는 것이 전부 여기 있다.
 *
 * 이 파일이 조용히 틀리면 **화면마다 다른 증상**으로 나타나서 원인을 찾기 어렵다.
 */

const original = globalThis.fetch;

function stubFetch(response: Response) {
  const calls: Array<{ url: string; init: RequestInit }> = [];
  globalThis.fetch = ((url: string, init: RequestInit) => {
    calls.push({ url, init });
    return Promise.resolve(response);
  }) as unknown as typeof fetch;
  return calls;
}

afterEach(() => {
  globalThis.fetch = original;
  tokenStore.clear();
});

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

describe("apiUrl", () => {
  test("상대 경로를 절대 주소로 만든다", () => {
    // **`fetch` 로 상대 경로를 그냥 부르면** API 가 다른 출처일 때 웹 서버로 가서
    // 404 가 된다 — 아바타 업로드가 실제로 그랬다.
    expect(apiUrl("/api/v1/problems").toString()).toContain("/api/v1/problems");
  });
});

describe("request 의 질의 문자열", () => {
  test("빈 값과 undefined 는 붙이지 않는다", async () => {
    const calls = stubFetch(json({ ok: true }));

    await request("/p", { query: { q: "합", category: "", tier: undefined, page: 2 } });

    const url = new URL(calls[0].url);
    expect(url.searchParams.get("q")).toBe("합");
    expect(url.searchParams.has("category")).toBe(false);
    expect(url.searchParams.has("tier")).toBe(false);
    expect(url.searchParams.get("page")).toBe("2");
  });

  /** 배열은 **같은 이름으로 여러 번** 붙인다 — 서버가 `List<String>` 으로 받는다 (#232). */
  test("배열은 같은 이름을 여러 번 붙인다", async () => {
    const calls = stubFetch(json({ ok: true }));

    await request("/p", { query: { runtime: ["python:3.12", "go:1.26"] } });

    expect(new URL(calls[0].url).searchParams.getAll("runtime")).toEqual(["python:3.12", "go:1.26"]);
  });

  test("배열 안의 빈 값은 버린다", async () => {
    const calls = stubFetch(json({ ok: true }));

    await request("/p", { query: { runtime: ["", "go:1.26"] } });

    expect(new URL(calls[0].url).searchParams.getAll("runtime")).toEqual(["go:1.26"]);
  });
});

describe("request 의 헤더", () => {
  test("auth 를 켜면 토큰을 싣는다", async () => {
    tokenStore.save({ accessToken: "토큰", refreshToken: "갱신" });
    const calls = stubFetch(json({ ok: true }));

    await request("/me", { auth: true });

    expect((calls[0].init.headers as Record<string, string>).Authorization).toBe("Bearer 토큰");
  });

  test("auth 를 켜도 토큰이 없으면 안 싣는다", async () => {
    const calls = stubFetch(json({ ok: true }));

    await request("/me", { auth: true });

    expect((calls[0].init.headers as Record<string, string>).Authorization).toBeUndefined();
  });

  /*
      **multipart 는 Content-Type 을 손으로 지정하면 안 된다** (#389).

      경계 문자열이 그 헤더에 들어가는데 그것은 브라우저가 만든다. 지정하면 서버가
      본문을 파싱하지 못한다 — 그리고 그 실패는 "업로드가 안 된다" 로만 보인다.
  */
  test("multipart 에는 Content-Type 을 붙이지 않는다", async () => {
    const calls = stubFetch(json({ ok: true }));
    const form = new FormData();
    form.append("file", new Blob(["x"]), "a.png");

    await request("/avatar", { method: "POST", body: form });

    const headers = calls[0].init.headers as Record<string, string>;
    expect(headers["Content-Type"]).toBeUndefined();
    expect(calls[0].init.body).toBe(form);
  });

  test("JSON 본문에는 Content-Type 을 붙이고 문자열로 만든다", async () => {
    const calls = stubFetch(json({ ok: true }));

    await request("/p", { method: "POST", body: { title: "A+B" } });

    expect((calls[0].init.headers as Record<string, string>)["Content-Type"]).toBe("application/json");
    expect(calls[0].init.body).toBe(JSON.stringify({ title: "A+B" }));
  });
});

describe("request 의 응답 처리", () => {
  test("204 는 본문 없이 끝난다", async () => {
    stubFetch(new Response(null, { status: 204 }));

    expect(await request("/p", { method: "DELETE" })).toBeUndefined();
  });

  test("본문이 비어도 깨지지 않는다", async () => {
    stubFetch(new Response("", { status: 200 }));

    expect(await request("/p")).toBeNull();
  });

  /**
   * 오류는 **서버가 준 코드 그대로** 올라간다.
   *
   * 화면은 `code` 로 갈라진다 (`SUBMISSION_TOO_FREQUENT`, `FEATURE_DISABLED`…).
   * 여기서 뭉개면 화면이 전부 "요청을 처리하지 못했습니다" 가 된다.
   */
  test("실패는 코드와 상태를 지닌 ApiError 가 된다", async () => {
    stubFetch(json({ code: "SUBMISSION_TOO_FREQUENT", message: "너무 잦습니다", fieldErrors: [] }, 429));

    const error = (await request("/s", { method: "POST" }).catch((it) => it)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe("SUBMISSION_TOO_FREQUENT");
    expect(error.status).toBe(429);
    expect(error.message).toBe("너무 잦습니다");
  });

  /** 서버가 우리 형식으로 답하지 못한 경우(502 등)에도 화면이 읽을 수 있어야 한다. */
  test("형식이 아닌 실패에도 기본 코드와 문구가 붙는다", async () => {
    stubFetch(new Response("", { status: 502 }));

    const error = (await request("/s").catch((it) => it)) as ApiError;

    expect(error.code).toBe("UNKNOWN");
    expect(error.status).toBe(502);
    expect(error.message).toBe("요청을 처리하지 못했습니다.");
  });
});
