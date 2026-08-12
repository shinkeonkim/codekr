import { SettingsPage } from "@/views/settings";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "설정",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <SettingsPage />;
}
