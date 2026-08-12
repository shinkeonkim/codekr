import { AdminQueuesPage } from "@/views/admin-queues";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "큐 모니터링 · 어드민",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <AdminQueuesPage />;
}
