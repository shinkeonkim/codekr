import { SharedCollectionPage } from "@/views/collection-detail";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "공유된 문제집",
  robots: { index: false, follow: false },
};


export default function Page({ params }: { params: Promise<{ token: string }> }) {
  return <SharedCollectionPage params={params} />;
}
