import type { NextConfig } from "next";

/**
 * 로컬에서도 `/api` 를 **같은 출처**로 만든다 (#314).
 *
 * `shared/config` 는 "배포에서는 Ingress 가 `/api` 를 api 로 보내므로 브라우저는
 * 자기가 열린 주소를 그대로 쓰면 된다" 를 전제로 한다. 그런데 로컬에서는 web 과 api
 * 의 출처가 달라 그 전제가 깨졌고, **`<img src>` 에는 `API_BASE_URL` 이 닿지 않아서**
 * 올린 아바타가 web 으로 요청돼 404 가 났다.
 *
 * 화면이 주소를 손보는 대신 여기서 넘긴다 — 그러면 로컬과 운영이 같은 방식으로
 * 동작하고, `<img>` 말고 다른 곳에서 같은 주소를 써도 또 손볼 일이 없다.
 *
 * 운영에서는 이 규칙에 닿지 않는다. Ingress 가 `/api` 를 api 로 먼저 보내므로 web
 * 파드는 그 요청을 보지도 못한다.
 *
 * **이 값은 빌드 시점에 굳는다.** Next 는 rewrite 를 `routes-manifest.json` 에 적어
 * 두고 실행 시 다시 읽지 않는다 — 그래서 컨테이너는 Dockerfile 의 build arg 로 받고,
 * 호스트에서 그냥 띄울 때를 위해 기본값을 둔다.
 */
const API_ORIGIN = process.env.API_INTERNAL_BASE_URL || "http://localhost:18080";

const nextConfig: NextConfig = {
  // 컨테이너 이미지를 얇게 유지하기 위해 standalone 출력을 쓴다.
  output: "standalone",
  reactStrictMode: true,

  async rewrites() {
    return [{ source: "/api/:path*", destination: `${API_ORIGIN}/api/:path*` }];
  },
};

export default nextConfig;
