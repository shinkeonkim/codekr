import { ContestDetailPage } from "@/views/contest-detail";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "대회",
};


export default function Page({ params }: { params: Promise<{ slug: string }> }) {
  return <ContestDetailPage params={params} />;
}
