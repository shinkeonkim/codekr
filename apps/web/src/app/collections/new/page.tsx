import { CollectionNewPage } from "@/views/collection-edit";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "새 문제집",
  robots: { index: false, follow: false },
};


export default function Page() {
  return <CollectionNewPage />;
}
