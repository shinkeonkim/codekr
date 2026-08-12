import { UserProfilePage } from "@/views/user-profile";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "프로필",
};

export default function Page(props: { params: Promise<{ nickname: string }> }) {
  return <UserProfilePage {...props} />;
}
