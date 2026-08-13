import { API_INTERNAL_BASE_URL } from "@/shared/config/server";
import { ContestDetailPage } from "@/views/contest-detail";
import type { Metadata } from "next";

/**
 * 목록에 없는 대회는 **색인하지 않는다** (#465, #278).
 *
 * `UNLISTED` 는 비밀이 아니지만 **검색으로 발견되는 것은 다른 일이다** — 링크를 받은
 * 사람만 오라고 만든 대회가 검색 결과에 뜨면 그 범위를 고른 뜻이 없어진다.
 *
 * sitemap(#234)에는 애초에 대회가 없다 — 고정 화면과 문제만 넣는다.
 *
 * **못 읽으면 색인하지 않는 쪽으로 기운다.** 여기서 실패했을 때 색인을 허용하면,
 * 서버가 잠깐 흔들린 사이에 비공개 대회가 색인될 수 있다.
 */
export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  const contest = await fetchContest(slug);
  const listed = contest?.summary?.visibility === "PUBLIC";

  return {
    title: contest?.summary?.title ?? "대회",
    robots: listed ? undefined : { index: false, follow: false },
  };
}

async function fetchContest(slug: string) {
  if (!API_INTERNAL_BASE_URL) return null;
  try {
    const response = await fetch(`${API_INTERNAL_BASE_URL}/api/v1/contests/${slug}`, {
      cache: "no-store",
    });
    if (!response.ok) return null;
    return (await response.json()) as { summary?: { title?: string; visibility?: string } };
  } catch {
    return null;
  }
}


export default function Page({ params }: { params: Promise<{ slug: string }> }) {
  return <ContestDetailPage params={params} />;
}
