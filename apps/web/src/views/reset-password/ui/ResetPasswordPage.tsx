"use client";

import { userApi } from "@/entities/user";
import { ApiError } from "@/shared/api";
import { Alert, Button, Card, Field, Input } from "@/shared/ui";
import Link from "next/link";
import { useState } from "react";

/** 새 비밀번호를 정하는 화면 (#315). 메일의 링크가 여기로 온다. */
export function ResetPasswordPage() {
  const [password, setPassword] = useState("");
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    setSaving(true);
    try {
      const token = new URLSearchParams(window.location.search).get("token") ?? "";
      await userApi.resetPassword(token, password);
      setDone(true);
      setError(null);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "바꾸지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="mx-auto max-w-md py-12">
      <Card className="space-y-3 p-6">
        <h1 className="text-lg font-bold text-ink">새 비밀번호</h1>

        {done ? (
          <>
            {/* **기존 세션이 끊긴다** — 다른 기기에서 열려 있던 것도 함께 (#315). */}
            <Alert tone="ok">
              비밀번호를 바꿨습니다. 다른 기기에 열려 있던 로그인도 함께 끊겼습니다.
            </Alert>
            <Link href="/login">
              <Button className="w-full">로그인하러 가기</Button>
            </Link>
          </>
        ) : (
          <>
            {error ? <Alert tone="danger">{error}</Alert> : null}
            <Field label="새 비밀번호 (8자 이상)">
              <Input
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </Field>
            <Button className="w-full" disabled={saving || password.length < 8} onClick={submit}>
              비밀번호 바꾸기
            </Button>
          </>
        )}
      </Card>
    </div>
  );
}
