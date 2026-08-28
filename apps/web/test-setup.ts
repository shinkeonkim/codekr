/**
 * 화면을 그려 보는 시험의 준비 (#646).
 *
 * bun 은 브라우저가 아니라 **`document` 가 없다.** happy-dom 을 전역에 등록해
 * `render()` 가 붙을 자리를 만든다. jsdom 이 아니라 happy-dom 인 이유는 훨씬 빠르고,
 * 우리가 그릴 것(Radix 를 쓰는 컴포넌트 포함)에 모자라지 않다는 것을 `SelectField`
 * 로 확인했기 때문이다 — **먼저 확인하고 골랐다.**
 *
 * `bunfig.toml` 의 `preload` 가 이 파일을 시험보다 먼저 돌린다.
 */
import { GlobalRegistrator } from "@happy-dom/global-registrator";
import { afterEach } from "bun:test";

/*
    **등록이 먼저다.** `@testing-library/react` 는 불러들일 때 `document` 를 본다.

    **주소를 준다.** 기본은 `about:blank` 인데, 그러면 `window.location.origin` 이
    쓸 수 없는 값이라 `new URL(path, origin)` 이 `TypeError: Invalid URL` 로 죽는다 —
    모든 서버 호출이 그 한 줄을 지난다(`apiUrl`). 브라우저에는 언제나 출처가 있으므로
    없는 상태를 흉내 낼 이유가 없다.
*/
GlobalRegistrator.register({ url: "http://localhost:3000" });

const { cleanup } = await import("@testing-library/react");

/*
    **시험 사이에 화면을 치운다.**

    안 치우면 앞 시험이 그린 것이 `document.body` 에 남아, `getByRole` 이 **여러 개를
    찾아 실패한다.** 그것도 나쁘지만 더 나쁜 경우가 있다 — 앞 시험의 요소를 찾아
    **통과해 버리는** 것이다. 그러면 지금 시험이 무엇을 확인했는지 알 수 없다.
*/
/*
    **여기서 정적으로 불러들여야 한다.** 처음에는 `afterEach` 안에서 동적으로
    불러들였는데, 그러면 시험이 도는 중에 `@testing-library/react` 가 처음 적재되고
    그 모듈이 자기 `beforeAll` 을 등록하려다 죽는다:

        error: Cannot call beforeAll() inside a test.

    화면을 안 그리는 시험 파일까지 통째로 실패했다.
*/
afterEach(cleanup);
