import { SharedCollectionPage } from "@/views/collection-detail";

export const metadata = { title: "문제집 · 코드.kr" };

export default function Page({ params }: { params: Promise<{ token: string }> }) {
  return <SharedCollectionPage params={params} />;
}
