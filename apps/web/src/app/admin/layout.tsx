"use client";

import { RequireAuth } from "@/features/auth";
import { AdminShell } from "@/widgets/admin-shell";
import type { ReactNode } from "react";

/**
 * 어드민 레이아웃 (#131, #179).
 *
 * **`RequireAuth adminOnly` 를 여기 한 번만 둔다.** 페이지마다 감싸면 새 어드민 화면을
 * 만들 때 빠뜨릴 수 있고, 빠뜨린 사실은 아무도 눈치채지 못한다.
 */
export default function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <RequireAuth adminOnly>
      <AdminShell>{children}</AdminShell>
    </RequireAuth>
  );
}
