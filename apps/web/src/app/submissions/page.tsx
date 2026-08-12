import { MySubmissionsPage } from "@/views/my-submissions";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "내 제출",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <MySubmissionsPage />;
}
