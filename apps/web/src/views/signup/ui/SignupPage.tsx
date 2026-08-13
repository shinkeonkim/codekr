"use client";

import { userApi } from "@/entities/user";
import { AuthForm, RequireGuest } from "@/features/auth";

/**
 * 회원가입 (#311).
 *
 * **로그인한 채로 이 화면을 열면 안 된다.** 가입 폼을 제출하면 `signIn(tokens)` 이
 * 돌아 원래 계정에서 조용히 로그아웃되고 새 계정으로 바뀐다 — 풀던 문제와 랭킹이
 * 있는 계정에서 튕겨 나가는데 화면에는 그 사실을 알리는 것이 없었다.
 */
export function SignupPage() {
  return (
    <RequireGuest notice="이미 로그인되어 있습니다. 다른 계정을 만들려면 먼저 로그아웃하세요.">
    <AuthForm
      mascot="welcome"
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
        userApi.signup({ email: values.email, password: values.password, nickname: values.nickname })
      }
    />
    </RequireGuest>
  );
}
