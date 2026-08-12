"use client";

import { useAuth } from "@/features/auth";
import { Hero } from "./Hero";
import { NoticeBoard } from "./NoticeBoard";
import { StartHere } from "./StartHere";
import { WelcomeBack } from "./WelcomeBack";

/**
 * 첫 화면 (#72, #231, #261, #263).
 *
 * **처음 온 사람과 매일 오는 사람에게 다른 것을 보여준다.** 소개는 처음 온 사람에게만
 * 필요하고, 이미 쓰는 사람에게는 "오늘 무엇을 할지" 가 필요하다.
 *
 * 그 아래는 두 사람에게 같다 — 공지와 풀 문제는 누구에게나 첫 화면의 내용이다.
 */
export function HomePage() {
  const { user, loading } = useAuth();

  return (
    <div className="space-y-12">
      {/* 판정 전에는 아무것도 그리지 않는다 — 소개가 떴다 사라지면 그것이 더 어수선하다. */}
      {loading ? null : user ? <WelcomeBack /> : <Hero />}

      <NoticeBoard />
      <StartHere />
    </div>
  );
}
