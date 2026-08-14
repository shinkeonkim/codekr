import { AdminProblemImportPage } from "@/views/admin-problem-import";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "문제 묶음 올리기 · 어드민",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <AdminProblemImportPage />;
}
