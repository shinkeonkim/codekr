import { CollectionEditPage } from "@/views/collection-edit";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "문제집 수정",
  robots: { index: false, follow: false },
};


export default function Page({ params }: { params: Promise<{ id: string }> }) {
  return <CollectionEditPage params={params} />;
}
