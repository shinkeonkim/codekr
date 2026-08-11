import { CollectionDetailPage } from "@/views/collection-detail";

export const metadata = { title: "문제집 · 코드.kr" };

export default function Page({ params }: { params: Promise<{ id: string }> }) {
  return <CollectionDetailPage params={params} />;
}
