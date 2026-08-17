import { FeedbackPage } from "@/views/feedback";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "신고·제안",
  description: "안 되는 것과 있었으면 하는 것을 알려 주세요.",
};

export default function Page() {
  return <FeedbackPage />;
}
