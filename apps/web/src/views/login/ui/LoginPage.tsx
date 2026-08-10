"use client";

import { userApi } from "@/entities/user";
import { AuthForm } from "@/features/auth";
export function LoginPage() {
  return (
    <AuthForm
      title="로그인"
      description="코드.kr 계정으로 문제를 풀고 채점 결과를 확인하세요."
      submitLabel="로그인"
      fields={[
        { name: "email", label: "이메일", type: "email", placeholder: "you@example.com", autoComplete: "email" },
        { name: "password", label: "비밀번호", type: "password", autoComplete: "current-password" },
      ]}
      footer={{ text: "아직 계정이 없으신가요?", href: "/signup", linkLabel: "회원가입" }}
      onSubmit={(values) => userApi.login({ email: values.email, password: values.password })}
    />
  );
}
