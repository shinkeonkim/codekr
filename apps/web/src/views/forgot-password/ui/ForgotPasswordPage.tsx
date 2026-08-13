"use client";

import { userApi } from "@/entities/user";
import { RequireGuest } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { Alert, Button, Card, Field, Input } from "@/shared/ui";
import Link from "next/link";
import { useState } from "react";

/**
 * 비밀번호 재설정 요청 (#315).
 *
 * 로그인·가입과 같은 짝(`RequireGuest`, #311)을 쓴다 — 로그인한 사람에게 보일 화면이
 * 아니다.
 */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  const submit = async () => {
    setSending(true);
    try {
      await userApi.requestPasswordReset(email.trim());
      setSent(true);
      setError(null);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "요청하지 못했습니다.");
    } finally {
      setSending(false);
    }
  };

  return (
    <RequireGuest>
      <div className="mx-auto max-w-md py-12">
        <Card className="space-y-3 p-6">
          <div>
            <h1 className="text-lg font-bold text-ink">비밀번호 재설정</h1>
            <p className="mt-1 text-sm text-ink-muted">가입한 이메일 주소를 알려 주세요.</p>
          </div>

          {sent ? (
            <>
              {/*
                **"보냈다" 가 아니라 "가입된 주소라면 보냈다" 다** (#315).

                서버가 가입 여부와 무관하게 같은 답을 준다 — 그래야 어느 주소가 가입되어
                있는지 확인하는 도구가 되지 않는다. 화면도 그에 맞춰 말해야 거짓말이
                되지 않는다.
              */}
              <Alert tone="ok">
                가입된 주소라면 재설정 링크를 보냈습니다. 메일함을 확인해 주세요.
              </Alert>
              <Link href="/login">
                <Button variant="secondary" className="w-full">
                  로그인으로
                </Button>
              </Link>
            </>
          ) : (
            <>
              {error ? <Alert tone="danger">{error}</Alert> : null}
              <Field label="이메일">
                <Input
                  type="email"
                  autoComplete="email"
                  placeholder="you@example.com"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                />
              </Field>
              <Button className="w-full" disabled={sending || !email.trim()} onClick={submit}>
                재설정 링크 받기
              </Button>
            </>
          )}
        </Card>
      </div>
    </RequireGuest>
  );
}
