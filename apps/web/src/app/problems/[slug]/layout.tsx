import { ProblemTabs } from "@/widgets/problem-tabs";
import type { ReactNode } from "react";

/** 문제 단위 컨텍스트(탭)를 세 화면이 공유한다. */
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
