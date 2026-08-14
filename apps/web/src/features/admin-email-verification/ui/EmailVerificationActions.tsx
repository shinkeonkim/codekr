"use client";

import { userApi } from "@/entities/user";
import { ApiError } from "@/shared/api";
import { Button } from "@/shared/ui";
import { useState } from "react";

/**
 * 어드민이 회원의 이메일 인증에 손대는 자리 (#524).
 *
 * **메일이 안 가면 그 사람은 글도 댓글도 못 쓴다** (#233). 스팸함으로 갔거나, 주소를
 * 오타로 적었거나, 발송이 실패했거나 — 지금까지는 그때 DB 를 직접 고치는 수밖에 없었다.
 *
 * **이미 인증한 사람에게는 아무것도 보이지 않는다.** 할 일이 없는 자리에 버튼이 있으면
 * 누를 이유를 찾게 된다.
 */
export function EmailVerificationActions({
  userId,
  verifiedAt,
  canForce,
  reason,
  onDone,
  onError,
}: {
  userId: number;
  verifiedAt: string | null;
  /** 강제 인증은 최고 관리자만 (#103 과 같은 자리). */
  canForce: boolean;
  /** 사유 칸은 관리 화면이 하나로 갖고 있다 (#225). */
  reason: string;
  onDone: (message: string) => void;
  onError: (message: string) => void;
}) {
  const [running, setRunning] = useState(false);

  if (verifiedAt) {
    return (
      <p className="text-xs text-ink-muted">
        이메일 확인함 · {new Date(verifiedAt).toLocaleDateString("ko-KR")}
      </p>
    );
  }

  const run = async (action: () => Promise<{ message: string }>) => {
    setRunning(true);
    try {
      // **서버가 만든 문장을 그대로 보인다.** "보냈다" 와 "갔다" 는 다른 말이고,
      // 그 차이를 아는 것은 서버뿐이다 — 화면이 따로 지어내면 둘이 갈라진다.
      onDone((await action()).message);
    } catch (caught) {
      onError(caught instanceof ApiError ? caught.message : "작업에 실패했습니다.");
    } finally {
      setRunning(false);
    }
  };

  return (
    <div className="space-y-2 rounded-lg border border-warn/40 bg-warn/5 p-2">
      <p className="text-xs text-ink-muted">
        <strong className="text-ink">이메일 미인증</strong> — 글·댓글을 쓸 수 없습니다.
      </p>
      <Button variant="ghost" disabled={running} onClick={() => run(() => userApi.resendEmailVerification(userId))}>
        인증 메일 다시 보내기
      </Button>
      {!canForce ? null : (
        <Button
          variant="ghost"
          disabled={running || reason.trim().length === 0}
          onClick={() => run(() => userApi.forceVerifyEmail(userId, reason.trim()))}
        >
          강제 인증 (사유 필요)
        </Button>
      )}
    </div>
  );
}
