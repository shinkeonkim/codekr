import { SubmissionExplorePage } from "@/views/submission-explore";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "전체 제출",
  description: "다른 회원이 어떤 문제를 어떤 언어로 풀었는지 살펴봅니다.",
};

export default function Page() {
  return <SubmissionExplorePage />;
}
