"use client";

import { userApi } from "@/entities/user";
import { AuthForm, useAuth } from "@/features/auth";
import { readNextParam } from "@/shared/lib";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

export function LoginPage() {
  const { user, loading } = useAuth();
  const router = useRouter();

  /*
   * 이미 로그인한 사람에게 로그인 화면을 보여주지 않는다 (#113, #73 과 같은 결).
   *
   * 특히 next 가 있으면 그리로 보낸다 — 다른 탭에서 로그인한 뒤 이 링크를 열었을 때
   * 다시 로그인하라고 하면 막힌 것처럼 보인다.
   */
  useEffect(() => {
    if (!loading && user) router.replace(readNextParam());
  }, [loading, user, router]);

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
