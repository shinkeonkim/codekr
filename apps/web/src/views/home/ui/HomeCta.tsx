"use client";

import { useAuth } from "@/features/auth";
import { Button } from "@/shared/ui";
import Link from "next/link";

/**
 * 첫 화면의 진입 버튼.
 *
 * 로그인한 사람에게 회원가입을 권하지 않는다 (#73). 이미 계정이 있는 사람에게
 * 가입 버튼을 보여주는 것은 화면이 로그인 상태를 모른다는 신호이고, 눌러도 의미 없는
 * 화면으로 간다.
 */
export function HomeCta() {
  const { user, loading } = useAuth();

  return (
    <div className="mt-8 flex items-center justify-center gap-3">
      <Link href="/problems">
        <Button>문제 풀러 가기</Button>
      </Link>
      {/* 판별 전에는 아무것도 보여주지 않는다 — 잠깐 나타났다 사라지는 편이 더 나쁘다. */}
      {loading ? null : user ? (
        <Link href="/submissions">
          <Button variant="secondary">내 제출 보기</Button>
        </Link>
      ) : (
        <Link href="/signup">
          <Button variant="secondary">회원가입</Button>
        </Link>
      )}
    </div>
  );
}
