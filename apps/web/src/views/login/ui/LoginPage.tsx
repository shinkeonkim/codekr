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
      // #261 은 로그인에 그림을 두지 않기로 했었다. #461 이 뒤집은 이유는 AuthForm 에 적었다.
      mascot="login"
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
        <div className="flex flex-col gap-1">
          <Link href="/forgot-password" className="text-ink-muted hover:text-ink hover:underline">
            비밀번호를 잊으셨나요?
          </Link>
          {/*
            **가장 급한 신고가 이 화면 바깥으로 못 나간다** (#611) — 가입·인증 메일·
            재설정이 안 되는 사람은 로그인해서 알릴 수 없다. 그 통로를 여기 둔다.
          */}
          <Link href="/help" className="text-ink-muted hover:text-ink hover:underline">
            로그인이 안 되시나요?
          </Link>
        </div>
      }
      onSubmit={(values) => userApi.login({ email: values.email, password: values.password })}
    />
    </RequireGuest>
  );
}
