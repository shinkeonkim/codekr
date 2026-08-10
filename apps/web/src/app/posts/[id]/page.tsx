import { PostDetailPage } from "@/views/post-detail";

export const metadata = { title: "게시글 · 코드.kr" };

export default function Page({ params }: { params: Promise<{ id: string }> }) {
  return <PostDetailPage params={params} />;
}
