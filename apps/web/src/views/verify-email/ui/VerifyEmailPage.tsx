"use client";

import { userApi } from "@/entities/user";
import { useAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { readNextParam } from "@/shared/lib";
import { Alert, Button, Card } from "@/shared/ui";
import Link from "next/link";
import { useEffect, useState } from "react";

type State = { kind: "working" } | { kind: "done" } | { kind: "failed"; message: string };

/**
 * 메일의 링크가 도착하는 곳 (#233).
 *
 * **로그인을 요구하지 않는다.** 메일을 받은 기기와 로그인한 기기가 다를 수 있고,
 * 토큰 자체가 본인 확인 수단이다.
 */
export function VerifyEmailPage() {
  const { user, refresh } = useAuth();
  const [state, setState] = useState<State>({ kind: "working" });

  useEffect(() => {
    let cancelled = false;
    /*
      토큰이 없는 경우도 **같은 흐름으로** 다룬다.

      효과 안에서 곧바로 상태를 바꾸면 렌더가 한 번 더 돌고(린트가 그것을 잡는다),
      서버가 그린 화면과도 어긋난다. 실패를 던져서 아래 catch 한 곳으로 모은다.
    */
    Promise.resolve()
      .then(() => {
        const token = new URLSearchParams(window.location.search).get("token");
        if (!token) throw new ApiError("VALIDATION_ERROR", "인증 링크가 올바르지 않습니다.", 400);
        return userApi.verifyEmail(token);
      })
      .then(() => {
        if (cancelled) return;
        setState({ kind: "done" });
        // 로그인한 채로 열었으면 헤더·설정이 곧바로 새 상태를 쓰게 한다.
        void refresh();
      })
      .catch((caught) => {
        if (cancelled) return;
        setState({
          kind: "failed",
          message: caught instanceof ApiError ? caught.message : "확인하지 못했습니다.",
        });
      });
    return () => {
      cancelled = true;
    };
  }, [refresh]);

  return (
    <div className="mx-auto max-w-md py-12">
      <Card className="space-y-3 p-6 text-center">
        <h1 className="text-lg font-bold text-ink">이메일 확인</h1>
        {state.kind === "working" ? <p className="text-sm text-ink-muted">확인하는 중…</p> : null}
        {state.kind === "done" ? (
          <>
            <p className="text-sm text-ink">주소를 확인했습니다.</p>
            <Button asChild className="w-full">
              <Link href={user ? readNextParam() : "/login"}>
                {user ? "계속하기" : "로그인하러 가기"}
              </Link>
            </Button>
          </>
        ) : null}
        {state.kind === "failed" ? (
          <>
            <Alert tone="danger">{state.message}</Alert>
            {/* 만료됐거나 이미 쓴 링크다. 다시 받는 곳은 설정이다. */}
            <Button asChild variant="secondary" className="w-full"><Link href="/settings">
                설정에서 다시 받기
              </Link></Button>
          </>
        ) : null}
      </Card>
    </div>
  );
}
