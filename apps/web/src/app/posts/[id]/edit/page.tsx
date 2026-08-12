import { PostEditPage } from "@/views/post-edit";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "글 수정",
  robots: { index: false, follow: false },
};


export default function Page({ params }: { params: Promise<{ id: string }> }) {
  return <PostEditPage params={params} />;
}
