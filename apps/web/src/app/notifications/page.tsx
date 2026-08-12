import { NotificationsPage } from "@/views/notifications";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "알림",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <NotificationsPage />;
}
