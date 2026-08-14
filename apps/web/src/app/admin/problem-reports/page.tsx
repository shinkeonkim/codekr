import { AdminProblemReportsPage } from "@/views/admin-problem-reports";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "문제 오류 신고 · 어드민",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <AdminProblemReportsPage />;
}
