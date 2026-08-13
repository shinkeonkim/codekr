import { ProblemTabs } from "@/widgets/problem-tabs";
import type { ReactNode } from "react";

/**
 * 문제 단위 컨텍스트(탭)를 **네 화면이 공유한다** — 내용·코드 제출·제출 내역·질문.
 *
 * **탭을 그리는 곳은 여기 하나다.** 질문 탭(#139)이 붙을 때 그 화면이 자기 탭을 직접
 * 들고 와서 **줄이 두 번 그려졌다** (#386). 화면이 탭을 그리기 시작하면 어느 화면이
 * 그리고 어느 화면이 안 그리는지 아무도 모르게 된다.
 */
export default async function ProblemLayout({
  children,
  params,
}: {
  children: ReactNode;
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  return (
    <div className="space-y-5">
      <ProblemTabs slug={slug} />
      {children}
    </div>
  );
}
