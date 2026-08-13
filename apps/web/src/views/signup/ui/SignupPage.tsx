"use client";

import { userApi } from "@/entities/user";
import { AuthForm, RequireGuest } from "@/features/auth";
import { TermsAgreement } from "@/features/terms";
import { useState } from "react";

/**
 * 회원가입 (#311).
 *
 * **로그인한 채로 이 화면을 열면 안 된다.** 가입 폼을 제출하면 `signIn(tokens)` 이
 * 돌아 원래 계정에서 조용히 로그아웃되고 새 계정으로 바뀐다 — 풀던 문제와 랭킹이
 * 있는 계정에서 튕겨 나가는데 화면에는 그 사실을 알리는 것이 없었다.
 */
export function SignupPage() {
  /*
    동의한 판의 id 목록 (#235).

    **개별 id 를 그대로 올린다** — "전체 동의" 라는 사실만 남기면 나중에 무엇에
    동의했는지 말할 수 없다. 필수를 다 못 받으면 서버가 가입을 거절하지만, 화면도
    미리 막는다 — 눌러야 알게 되는 것은 안내가 아니다.
  */
  const [agreedTermIds, setAgreedTermIds] = useState<number[]>([]);
  const [requiredIds, setRequiredIds] = useState<number[]>([]);
  const canSubmit = requiredIds.every((id) => agreedTermIds.includes(id));

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
      beforeSubmit={
        <TermsAgreement onChange={setAgreedTermIds} onRequiredChange={setRequiredIds} />
      }
      submitDisabled={!canSubmit}
      onSubmit={(values) =>
        userApi.signup({
          email: values.email,
          password: values.password,
          nickname: values.nickname,
          agreedTermIds,
        })
      }
    />
    </RequireGuest>
  );
}
