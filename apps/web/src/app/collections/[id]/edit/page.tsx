import { CollectionEditPage } from "@/views/collection-edit";

export const metadata = { title: "문제집 수정 · 코드.kr" };

export default function Page({ params }: { params: Promise<{ id: string }> }) {
  return <CollectionEditPage params={params} />;
}
