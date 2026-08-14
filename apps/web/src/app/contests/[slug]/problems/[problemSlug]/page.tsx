import { ContestSolvePage } from "@/views/contest-solve";

export const metadata = { title: "대회 문제 풀기", robots: { index: false, follow: false } };

export default function Page({
  params,
}: {
  params: Promise<{ slug: string; problemSlug: string }>;
}) {
  return <ContestSolvePage params={params} />;
}
