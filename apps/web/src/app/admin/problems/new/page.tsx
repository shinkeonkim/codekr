import { AdminProblemNewPage } from "@/views/admin-problem-new";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "문제 등록 · 어드민",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <AdminProblemNewPage />;
}
