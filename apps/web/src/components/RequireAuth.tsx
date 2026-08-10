"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import type { ReactNode } from "react";
import { useAuth } from "@/lib/auth";

/**
 * 로그인(및 선택적으로 어드민 권한)이 필요한 화면을 감싼다.
 * 서버 쪽 권한 검사가 최종 방어선이고, 이것은 사용자 경험을 위한 안내 역할이다.
 */
export function RequireAuth({ children, adminOnly = false }: { children: ReactNode; adminOnly?: boolean }) {
  const { user, isAdmin, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (loading) return;
    if (!user) router.replace("/login");
    else if (adminOnly && !isAdmin) router.replace("/problems");
  }, [loading, user, isAdmin, adminOnly, router]);

  if (loading) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;
  if (!user || (adminOnly && !isAdmin)) {
    return <p className="py-16 text-center text-sm text-ink-muted">접근 권한을 확인하는 중…</p>;
  }
  return <>{children}</>;
}
