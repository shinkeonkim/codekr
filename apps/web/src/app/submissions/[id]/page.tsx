import { SubmissionDetailPage } from "@/views/submission-detail";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "제출",
  robots: { index: false, follow: false },
};

export default function Page(props: { params: Promise<{ id: string }> }) {
  return <SubmissionDetailPage {...props} />;
}
