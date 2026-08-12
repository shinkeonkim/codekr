import { SignupPage } from "@/views/signup";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "회원가입",
  description: "이메일 하나로 가입하고 푼 문제를 기록합니다.",
};

export default function Page() {
  return <SignupPage />;
}
