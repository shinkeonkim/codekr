import { AdminProblemListPage } from "@/views/admin-problem-list";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "문제 관리 · 어드민",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <AdminProblemListPage />;
}
