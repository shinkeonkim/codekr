"use client";

import { userApi } from "@/entities/user";
import { useAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { Alert, Button, Card, useToast } from "@/shared/ui";
import { useState } from "react";

/**
 * 이메일 확인 안내와 재발송 (#233).
 *
 * **확인이 끝났으면 아무것도 그리지 않는다.** 끝난 일을 계속 보여 주면 설정 화면이
 * 길어지기만 하고, 할 일이 남은 사람의 안내가 그만큼 묻힌다.
 */
export function EmailVerificationCard() {
  const { user } = useAuth();
  const toast = useToast();
  const [sending, setSending] = useState(false);

  if (!user || user.emailVerified) return null;

  const resend = async () => {
    setSending(true);
    try {
      await userApi.resendVerification();
      toast.success("인증 메일을 다시 보냈습니다.");
    } catch (caught) {
      // 쿨다운·하루 상한은 서버가 남은 시간까지 담아 준다 (#233).
      toast.error(caught instanceof ApiError ? caught.message : "보내지 못했습니다.");
    } finally {
      setSending(false);
    }
  };

  return (
    <Card className="space-y-3 p-5">
      <div>
        <h2 className="text-sm font-semibold text-ink">이메일 확인</h2>
        <p className="mt-1 text-xs text-ink-muted">
          가입할 때 <span className="text-ink">{user.email}</span> 로 보낸 링크를 누르면 끝납니다.
        </p>
      </div>
      {/* 무엇이 막히는지 먼저 말한다 — 막히고 나서 알면 늦다. */}
      <Alert tone="warn">확인하기 전에는 글과 댓글을 쓸 수 없습니다.</Alert>
      <Button variant="secondary" disabled={sending} onClick={resend}>
        인증 메일 다시 받기
      </Button>
    </Card>
  );
}
