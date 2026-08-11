import { ContestDetailPage } from "@/views/contest-detail";

export const metadata = { title: "대회 · 코드.kr" };

export default function Page({ params }: { params: Promise<{ slug: string }> }) {
  return <ContestDetailPage params={params} />;
}
