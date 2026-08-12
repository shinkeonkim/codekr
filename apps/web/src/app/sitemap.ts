import { API_INTERNAL_BASE_URL } from "@/shared/config/server";
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
  const fixed = ["/", ...Object.values(PUBLIC_ROUTES).flatMap((links) => links.map((l) => l.href))];
  const problems = await problemPaths();

  return [...fixed, ...problems].map((path) => ({
    url: `${base}${path}`,
    changeFrequency: path === "/" ? "daily" : "weekly",
    // 첫 화면만 1.0. 나머지에 굳이 등급을 매기지 않는다 — 근거 없는 숫자를 넣느니
    // 크롤러의 판단에 맡긴다.
    priority: path === "/" ? 1 : 0.7,
  }));
}

/** 한 번에 받아 오는 문제 수. 서버가 허용하는 최댓값이다. */
const PAGE_SIZE = 100;

/**
 * 문제 상세 주소 (#271).
 *
 * **공개 목록 API 만 쓴다.** 어드민 API 를 부르면 미공개 문제가 sitemap 으로 새어
 * 나간다 — 서버가 이미 `published = true` 만 내려주므로 여기서 다시 거르지 않는다.
 *
 * 서버 전용 주소가 없으면(로컬·미리보기) **고정 화면만 내보낸다.** sitemap 때문에
 * 개발이 막히면 안 된다.
 *
 * 실패해도 빈 배열이다. 문제 목록을 못 받았다고 sitemap 전체가 500 이 되면, 있던
 * 고정 화면마저 색인에서 빠진다.
 */
async function problemPaths(): Promise<string[]> {
  if (!API_INTERNAL_BASE_URL) return [];

  try {
    const paths: string[] = [];
    // 쪽수를 따라간다. 첫 응답이 전체 쪽수를 알려 준다.
    for (let page = 0; ; page++) {
      const response = await fetch(
        `${API_INTERNAL_BASE_URL}/api/v1/problems?page=${page}&size=${PAGE_SIZE}`,
        // 매 요청마다 문제 목록을 다시 받을 이유가 없다. 크롤러는 자주 오지 않는다.
        { next: { revalidate: 3600 } },
      );
      if (!response.ok) break;

      const body = (await response.json()) as { content: { slug: string }[]; totalPages: number };
      paths.push(...body.content.map((problem) => `/problems/${problem.slug}`));
      if (page + 1 >= body.totalPages) break;
    }
    return paths;
  } catch {
    return [];
  }
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
