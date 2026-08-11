import { PostEditPage } from "@/views/post-edit";

export const metadata = { title: "글 수정 · 코드.kr" };

export default function Page({ params }: { params: Promise<{ id: string }> }) {
  return <PostEditPage params={params} />;
}
