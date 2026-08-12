"use client";

import { useAuth } from "@/features/auth";
import { Hero } from "./Hero";
import { StartHere } from "./StartHere";

/**
 * 첫 화면 (#72, #261).
 *
 * 방문자가 알고 싶은 것은 "이 사이트가 나에게 무엇을 해주는가" 이지 "이 사이트가 어떻게
 * 동작하는가"가 아니다. 그래서 기능을 나열하는 대신 **지금 풀 문제**를 바로 보여준다.
 *
 * **처음 온 사람과 매일 오는 사람에게 다른 것을 보여준다** (#231 의 방향).
 * 소개는 처음 온 사람에게만 필요하다 — 이미 쓰는 사람에게는 지나가야 할 벽이다.
 * 로그인한 사람의 대시보드(스트릭·이어서 풀 문제)는 #231 에서 만든다.
 */
export function HomePage() {
  const { user, loading } = useAuth();

  return (
    <div className="space-y-12">
      {/* 판정 전에는 아무것도 그리지 않는다 — 소개가 떴다 사라지면 그것이 더 어수선하다. */}
      {loading || user ? null : <Hero />}

      {user ? (
        <section className="pt-2">
          <h1 className="text-2xl font-bold tracking-tight text-ink">
            오늘 한 문제, 이어서 풀까요?
          </h1>
        </section>
      ) : null}

      <StartHere />
    </div>
  );
}
