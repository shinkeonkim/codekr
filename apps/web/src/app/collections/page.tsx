import { CollectionListPage } from "@/views/collection-list";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "문제집",
  description: "주제별로 묶인 문제를 순서대로 풉니다.",
};


export default function Page() {
  return <CollectionListPage />;
}
