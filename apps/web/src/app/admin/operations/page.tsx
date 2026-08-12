import { AdminOperationsPage } from "@/views/admin-operations";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "운영 작업 · 어드민",
  robots: { index: false, follow: false },
};


export default function Page() {
  return <AdminOperationsPage />;
}
