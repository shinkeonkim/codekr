import { AdminStatsPage } from "@/views/admin-stats";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "통계 · 어드민",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <AdminStatsPage />;
}
