import { NO_INDEX_PATHS } from "@/shared/config/routes";
import type { MetadataRoute } from "next";
import { headers } from "next/headers";

/**
 * robots.txt (#234).
 *
 * 어드민과 개인 화면을 색인에서 뺀다. 미공개 문제는 **애초에 목록에 나오지 않으므로**
 * (서버가 `published = true` 만 내려준다) 크롤러가 주소를 알아낼 길이 없다 — robots.txt
 * 로 막을 대상이 아니다. 오히려 여기 적으면 "이런 경로가 있다" 고 알려 주는 셈이 된다.
 *
 * sitemap 주소도 요청의 Host 에서 만든다 — 이유는 `sitemap.ts` 에 적어 두었다.
 */
export default async function robots(): Promise<MetadataRoute.Robots> {
  const headerList = await headers();
  const host = headerList.get("host") ?? "코드.kr";
  const proto = headerList.get("x-forwarded-proto") ?? (host.startsWith("localhost") ? "http" : "https");

  return {
    rules: { userAgent: "*", allow: "/", disallow: NO_INDEX_PATHS },
    sitemap: `${proto}://${host}/sitemap.xml`,
  };
}
