import { CollectionDetailPage } from "@/views/collection-detail";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "문제집",
};


export default function Page({ params }: { params: Promise<{ id: string }> }) {
  return <CollectionDetailPage params={params} />;
}
