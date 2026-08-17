import { AdminFeedbacksPage } from "@/views/admin-feedbacks";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "신고·제안 · 어드민",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <AdminFeedbacksPage />;
}
