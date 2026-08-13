"use client";

import { siteStatsApi } from "@/entities/problem";
import { Button } from "@/shared/ui";
import Link from "next/link";
import { useEffect, useState } from "react";
import { HeroFrame } from "./HeroFrame";

/**
 * 처음 온 사람에게 보이는 첫 화면의 글 (#261, #231, #282).
 *
 * 이미 쓰는 사람에게 소개는 지나가야 할 벽이다 — 그 사람의 자리에는 `WelcomeBack` 의
 * 글이 들어간다. **틀(`HeroFrame`)은 둘이 같은 것을 쓴다.**
 *
 * 그림 안의 글자(원본 히어로의 왼쪽 패널)는 쓰지 않는다. 화면 폭에 맞춰 줄이 바뀌지
 * 않고, 스크린 리더가 읽지 못하며, 문구를 고치려면 그림을 다시 그려야 한다.
 * **글자는 글자로 쓴다.**
 */
export function Hero() {
  const [stats, setStats] = useState<{ problemCount: number; runtimeCount: number } | null>(null);

  useEffect(() => {
    siteStatsApi.fetch().then(setStats).catch(() => setStats(null));
  }, []);

  return (
    <HeroFrame>
      <h1 className="text-4xl font-bold tracking-tight text-ink sm:text-5xl">
        문제로 성장하는
        <br />
        <span className="text-brand">개발자들의 온라인 저지</span>
      </h1>
      <p className="mx-auto mt-4 max-w-md text-base leading-relaxed text-ink-muted lg:mx-0">
        알고리즘·SQL·CS 문제를 풀고, 채점이 도는 과정을 실시간으로 보면서 코딩 실력을
        증명하세요.
      </p>

      {/*
        **눈에 보이는 증거** (#231). 처음 온 사람은 세 줄 안에 여기서 무엇을 얻는지
        알아야 하는데, 문장만으로는 그것이 실제로 도는 사이트인지 알 수 없다.

        못 받아 오면 그 줄을 아예 그리지 않는다 — "0문제" 가 보이는 것이 훨씬 나쁘다.
      */}
      {stats ? (
        <p className="mt-3 text-sm text-ink-muted">
          지금 <span className="font-semibold text-ink">{stats.problemCount}문제</span> ·
          <span className="font-semibold text-ink"> {stats.runtimeCount}개 언어</span>로 풀 수 있습니다
        </p>
      ) : null}

      <div className="mt-6 flex flex-wrap justify-center gap-2 lg:justify-start">
        <Link href="/problems">
          <Button>지금 시작하기</Button>
        </Link>
        <Link href="/signup">
          <Button variant="secondary">회원가입</Button>
        </Link>
      </div>
    </HeroFrame>
  );
}
