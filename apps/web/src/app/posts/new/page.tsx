import { PostNewPage } from "@/views/post-edit";
import { Suspense } from "react";

export const metadata = { title: "새 글 · 코드.kr" };

export default function Page() {
  // 문제 질문에서 넘어올 때 ?problemId= 를 읽는다 (#139).
  // useSearchParams 를 쓰면 정적 생성에 Suspense 경계가 필요하다.
  return (
    <Suspense fallback={<p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>}>
      <PostNewPage />
    </Suspense>
  );
}
