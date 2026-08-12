import { AdminProblemEditPage } from "@/views/admin-problem-edit";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "문제 수정 · 어드민",
  robots: { index: false, follow: false },
};

export default function Page(props: { params: Promise<{ id: string }> }) {
  return <AdminProblemEditPage {...props} />;
}
