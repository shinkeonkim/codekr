"use client";

import { userApi } from "@/entities/user";
import { AuthForm, RequireGuest } from "@/features/auth";
import Link from "next/link";

/**
 * 이미 로그인한 사람에게 로그인 화면을 보여주지 않는다 (#113, #73 과 같은 결).
 *
 * 가드는 [RequireGuest] 가 한다 (#311) — 손으로 쓴 `useEffect` 는 한 번 그려진
 * 뒤에 돌아서 폼이 번쩍였다. next 로 돌려보내는 동작은 그대로다.
 */
export function LoginPage() {
  return (
    <RequireGuest>
    <AuthForm
      title="로그인"
      description="코드.kr 계정으로 문제를 풀고 채점 결과를 확인하세요."
      submitLabel="로그인"
      fields={[
        { name: "email", label: "이메일", type: "email", placeholder: "you@example.com", autoComplete: "email" },
        { name: "password", label: "비밀번호", type: "password", autoComplete: "current-password" },
      ]}
      footer={{ text: "아직 계정이 없으신가요?", href: "/signup", linkLabel: "회원가입" }}
      // **그 자리가 없었다** (#315) — 잊으면 계정을 잃는 것과 같았다.
      aside={
        <Link href="/forgot-password" className="text-ink-muted hover:text-ink hover:underline">
          비밀번호를 잊으셨나요?
        </Link>
      }
      onSubmit={(values) => userApi.login({ email: values.email, password: values.password })}
    />
    </RequireGuest>
  );
}
