import { AdminUserListPage } from "@/views/admin-user-list";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "회원 관리 · 어드민",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <AdminUserListPage />;
}
