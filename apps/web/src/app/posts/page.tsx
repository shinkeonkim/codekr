import { PostListPage } from "@/views/post-list";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "게시판",
  description: "질문하고, 풀이를 나누고, 공지를 받습니다.",
};


export default function Page() {
  return <PostListPage />;
}
