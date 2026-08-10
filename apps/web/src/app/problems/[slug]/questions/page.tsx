import { ProblemQuestionsPage } from "@/views/problem-questions";

export const metadata = { title: "질문 · 코드.kr" };

export default function Page({ params }: { params: Promise<{ slug: string }> }) {
  return <ProblemQuestionsPage params={params} />;
}
