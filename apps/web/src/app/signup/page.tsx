"use client";

import { AuthForm } from "@/components/AuthForm";
import { api } from "@/lib/api";

export default function SignupPage() {
  return (
    <AuthForm
      title="회원가입"
      description="이메일과 비밀번호만 있으면 바로 시작할 수 있습니다."
      submitLabel="가입하고 시작하기"
      fields={[
        { name: "email", label: "이메일", type: "email", placeholder: "you@example.com", autoComplete: "email" },
        {
          name: "password",
          label: "비밀번호 (8자 이상)",
          type: "password",
          autoComplete: "new-password",
        },
        { name: "nickname", label: "닉네임", type: "text", placeholder: "코더", autoComplete: "nickname" },
      ]}
      footer={{ text: "이미 계정이 있으신가요?", href: "/login", linkLabel: "로그인" }}
      onSubmit={(values) =>
        api.signup({ email: values.email, password: values.password, nickname: values.nickname })
      }
    />
  );
}
