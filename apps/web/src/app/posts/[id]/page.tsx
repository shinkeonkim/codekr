import { PostDetailPage } from "@/views/post-detail";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "게시글",
};


export default function Page({ params }: { params: Promise<{ id: string }> }) {
  return <PostDetailPage params={params} />;
}
