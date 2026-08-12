"use client";

import { readNextParam } from "@/shared/lib";
import { useToast } from "@/shared/ui";
import { useRouter } from "next/navigation";
import { useEffect, useRef } from "react";
import type { ReactNode } from "react";
import { useAuth } from "../model/AuthProvider";
import { guestGate } from "../model/guestGate";

/**
 * 로그인한 사람에게 보이면 안 되는 화면을 감싼다 — [RequireAuth] 의 짝 (#311).
 *
 * **#73 이 회원가입 링크를 감췄지만 화면은 열어 두었다.** 주소를 치거나 북마크로
 * 들어오면 가입 폼이 그대로 뜨고, 채워서 제출하면 `signIn(tokens)` 이 돌아
 * **원래 계정에서 조용히 로그아웃되고 새 계정으로 바뀐다.**
 *
 * 두 화면이 같은 부품을 쓰게 한 이유는 한쪽만 고쳐지는 일을 막기 위해서다 —
 * 비밀번호 재설정(#233 이후) 같은 화면이 생기면 같은 짝이 필요해진다.
 */
export function RequireGuest({ children, notice }: { children: ReactNode; notice?: string }) {
  const { user, loading } = useAuth();
  const router = useRouter();
  const toast = useToast();
  const notified = useRef(false);

  const gate = guestGate(loading, Boolean(user));

  useEffect(() => {
    if (gate !== "redirect" || notified.current) return;
    notified.current = true;
    // **아무 말 없이 옮기면 눌러도 아무 일도 안 일어난 것처럼 보인다.**
    if (notice) toast.info(notice);
    // next 가 있으면 그리로 — 다른 탭에서 로그인한 뒤 이 링크를 열었을 때 필요하다.
    router.replace(readNextParam());
  }, [gate, notice, router, toast]);

  if (gate === "wait") return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;
  if (gate === "redirect") return null;
  return <>{children}</>;
}
