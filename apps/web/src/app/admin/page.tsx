import { AdminHomePage } from "@/views/admin-home";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "어드민",
  robots: { index: false, follow: false },
};


export default function Page() {
  return <AdminHomePage />;
}
