import { Button } from "@/shared/ui";
import Image from "next/image";
import Link from "next/link";

/**
 * 처음 온 사람에게 보이는 첫 화면 (#261, #231).
 *
 * **이미 쓰는 사람에게는 보이지 않는다.** 매일 오는 사람에게 소개는 지나가야 할 벽이다 —
 * 그 사람의 첫 화면은 "오늘 무엇을 할지" 여야 하고, 그것은 #231 의 몫이다.
 *
 * 그림 안의 글자(원본 히어로의 왼쪽 패널)는 쓰지 않는다. 화면 폭에 맞춰 줄이 바뀌지
 * 않고, 스크린 리더가 읽지 못하며, 문구를 고치려면 그림을 다시 그려야 한다.
 * **글자는 글자로 쓴다.**
 */
export function Hero() {
  return (
    <section className="grid items-center gap-8 pt-4 lg:grid-cols-[1fr_1.1fr] lg:gap-10">
      <div className="text-center lg:text-left">
        <h1 className="text-4xl font-bold tracking-tight text-ink sm:text-5xl">
          문제로 성장하는
          <br />
          <span className="text-brand">개발자들의 온라인 저지</span>
        </h1>
        <p className="mx-auto mt-4 max-w-md text-base leading-relaxed text-ink-muted lg:mx-0">
          알고리즘·SQL·CS 문제를 풀고, 채점이 도는 과정을 실시간으로 보면서 코딩 실력을
          증명하세요.
        </p>

        <div className="mt-6 flex flex-wrap justify-center gap-2 lg:justify-start">
          <Link href="/problems">
            <Button>지금 시작하기</Button>
          </Link>
          <Link href="/signup">
            <Button variant="secondary">회원가입</Button>
          </Link>
        </div>
      </div>

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
