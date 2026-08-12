import Image from "next/image";
import type { ReactNode } from "react";

/**
 * 첫 화면 맨 위의 틀 (#282).
 *
 * **로그인 여부와 무관하게 같은 자리·같은 크기다.** 전에는 비로그인만 이 큰 덩어리를
 * 보고 로그인하면 작은 카드로 바뀌었다 — 같은 화면인데 로그인했다는 이유로 위쪽이
 * 갑자기 작아졌다. 갈려야 하는 것은 **내용**이지 크기가 아니다 (#231).
 *
 * 틀을 여기 한 번만 적는다. 두 상태가 각자 격자를 들고 있으면 한쪽만 고쳐진다.
 */
export function HeroFrame({ children }: { children: ReactNode }) {
  return (
    <section className="grid items-center gap-8 pt-4 lg:grid-cols-[0.9fr_1.3fr] lg:gap-12">
      <div className="text-center lg:text-left">{children}</div>

      {/*
        `priority` 인 이유: 첫 화면에서 가장 큰 그림이라 늦게 오면 글자가 밀린다.
        alt 를 비운 이유: 옆의 제목·설명이 이미 같은 것을 말한다. 그림을 다시 설명하면
        스크린 리더 사용자가 같은 내용을 두 번 듣는다.
      */}
      <Image
        src="/brand/hero.webp"
        alt=""
        width={1280}
        height={853}
        priority
        className="w-full rounded-card border border-border"
      />
    </section>
  );
}
