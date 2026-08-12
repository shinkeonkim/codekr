import { PUBLIC_ROUTES } from "@/shared/config/routes";
import type { MetadataRoute } from "next";
import { headers } from "next/headers";

/**
 * sitemap.xml (#234).
 *
 * **주소를 빌드에 박지 않는다.** `NEXT_PUBLIC_*` 은 번들에 들어가므로 주소를 박으면
 * 환경마다 이미지를 따로 빌드해야 한다 (`shared/config` 에 같은 이유가 적혀 있다).
 * 대신 요청의 Host 를 읽어 만든다 — 공개 주소로 들어오면 공개 주소가, 내부 주소로
 * 들어오면 내부 주소가 나온다.
 *
 * 그 대가로 이 파일은 **요청 시점에 만들어진다**(Host 는 요청 API 다). 정적으로
 * 캐시되지 않지만, 만드는 일이 배열 하나를 도는 것뿐이라 값이 싸다.
 */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const base = await origin();
  const paths = ["/", ...Object.values(PUBLIC_ROUTES).flatMap((links) => links.map((l) => l.href))];

  return paths.map((path) => ({
    url: `${base}${path}`,
    changeFrequency: path === "/" ? "daily" : "weekly",
    // 첫 화면만 1.0. 나머지에 굳이 등급을 매기지 않는다 — 근거 없는 숫자를 넣느니
    // 크롤러의 판단에 맡긴다.
    priority: path === "/" ? 1 : 0.7,
  }));
}

/**
 * 지금 열린 출처.
 *
 * Host 헤더는 요청하는 쪽이 정하므로 **믿을 수 있는 값이 아니다.** 여기서는 그것으로
 * 충분하다 — 틀린 Host 로 부르면 틀린 sitemap 을 자기가 받을 뿐, 다른 사람에게
 * 영향을 주지 않는다. 링크에 쓰거나 저장하는 자리였다면 이렇게 쓰면 안 된다.
 */
async function origin(): Promise<string> {
  const headerList = await headers();
  const host = headerList.get("host") ?? "코드.kr";
  // 프록시 뒤에서는 원래 스킴이 여기에 남는다. 없으면 로컬 개발이라고 본다.
  const proto = headerList.get("x-forwarded-proto") ?? (host.startsWith("localhost") ? "http" : "https");
  return `${proto}://${host}`;
}
