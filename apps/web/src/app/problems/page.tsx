import { ProblemListPage } from "@/views/problem-list";
import { Suspense } from "react";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "문제",
  description: "알고리즘·SQL·CS 문제를 난이도와 유형으로 골라 풉니다.",
};


export default function Page() {
  // 목록 상태를 URL 에 두므로 useSearchParams 를 쓴다 (#132).
  // 정적 생성에는 Suspense 경계가 필요하다 — 없으면 빌드가 깨진다.
  return (
    <Suspense fallback={<p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>}>
      <ProblemListPage />
    </Suspense>
  );
}
