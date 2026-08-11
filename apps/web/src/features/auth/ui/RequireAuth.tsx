"use client";

import { currentNextParam } from "@/shared/lib";
import { useToast } from "@/shared/ui";
import { useRouter } from "next/navigation";
import { useEffect, useRef } from "react";
import type { ReactNode } from "react";
import { useAuth } from "../model/AuthProvider";

/**
 * 로그인(및 선택적으로 어드민 권한)이 필요한 화면을 감싼다.
 * 서버 쪽 권한 검사가 최종 방어선이고, 이것은 사용자 경험을 위한 안내 역할이다.
 *
 * 튕길 때 **왜 튕겼는지 알리고, 가려던 곳을 기억한다** (#113).
 * 말없이 보내면 사이트가 고장 난 것으로 보이고, 공유받은 링크는 로그인 후 다시 찾아가야 한다.
 */
export function RequireAuth({ children, adminOnly = false }: { children: ReactNode; adminOnly?: boolean }) {
  const { user, isAdmin, loading } = useAuth();
  const router = useRouter();
  const toast = useToast();

  // 이펙트가 다시 돌아도 토스트가 두 번 뜨지 않게 한 번만 알린다.
  const notified = useRef(false);

  useEffect(() => {
    if (loading || notified.current) return;

    if (!user) {
      notified.current = true;
      toast.info("로그인이 필요한 화면입니다.");
      router.replace(`/login?next=${currentNextParam()}`);
      return;
    }

    if (adminOnly && !isAdmin) {
      notified.current = true;
      // 로그인은 되어 있으므로 "로그인하라" 가 아니라 "권한이 없다" 여야 한다.
      toast.error("이 화면에 접근할 권한이 없습니다.");
      router.replace("/problems");
    }
  }, [loading, user, isAdmin, adminOnly, router, toast]);

  if (loading) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;
  // 튕기는 이유는 토스트가 말한다. 여기서 또 문구를 두면 중복이다.
  if (!user || (adminOnly && !isAdmin)) return null;
  return <>{children}</>;
}
