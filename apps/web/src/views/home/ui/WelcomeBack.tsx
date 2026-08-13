"use client";

import { activityApi } from "@/entities/activity";
import { submissionApi } from "@/entities/submission";
import type { SubmissionSummary } from "@/entities/submission";
import { useAuth } from "@/features/auth";
import { Button } from "@/shared/ui";
import Link from "next/link";
import { useEffect, useState } from "react";
import { HeroFrame } from "./HeroFrame";

/**
 * 이미 쓰는 사람의 첫 화면 (#231, #261, #282).
 *
 * 소개는 이 사람에게 지나가야 할 벽이다. 대신 **오늘 무엇을 할지**를 정해 준다 —
 * 이어 온 날이 며칠인지, 지금 바로 누를 곳이 어디인지.
 *
 * **자리와 크기는 소개와 같다** (`HeroFrame`). 로그인했다는 이유로 화면 위쪽이 작아지면
 * 같은 사이트가 다르게 보인다 — 갈려야 하는 것은 내용이다.
 *
 * **가입 유도는 없다** (#73). 이미 가입한 사람에게 권할 것이 아니고, 여기 두면
 * "내가 로그인이 안 되어 있나" 하고 한 번 멈추게 만든다.
 */
export function WelcomeBack() {
  const { user } = useAuth();
  const [streak, setStreak] = useState<number | null>(null);
  const [unsolved, setUnsolved] = useState<SubmissionSummary | null>(null);

  useEffect(() => {
    activityApi
      .mine()
      .then((activity) => setStreak(activity.currentStreak))
      .catch(() => setStreak(null));
  }, []);

  useEffect(() => {
    /*
      **이어서 풀 문제** (#231).

      최근에 냈지만 아직 못 맞힌 문제다 — "오늘 무엇을 할지" 를 정해 주는 자리에서
      가장 값이 있는 한 줄이고, 이미 있는 API 로 만들 수 있다. 첫 화면 전용 API 를
      새로 만들면 같은 값을 두 곳에서 세게 된다.
    */
    submissionApi
      .mine({ size: 20 })
      .then((page) => {
        const solved = new Set(
          page.content.filter((each) => each.verdict === "ACCEPTED").map((each) => each.problemSlug),
        );
        setUnsolved(
          page.content.find(
            (each) => each.problemId !== null && each.verdict !== null && !solved.has(each.problemSlug),
          ) ?? null,
        );
      })
      .catch(() => setUnsolved(null));
  }, []);

  const { headline, note } = greeting(user?.nickname ?? "", streak);

  return (
    <HeroFrame>
      <h1 className="text-4xl font-bold tracking-tight text-ink sm:text-5xl">{headline}</h1>
      <p className="mx-auto mt-4 max-w-md text-base leading-relaxed text-ink-muted lg:mx-0">
        {note}
      </p>

      {/* 못 맞힌 문제가 있으면 그것부터 권한다. 없으면 이 줄이 아예 없다. */}
      {unsolved?.problemId ? (
        <p className="mt-3 text-sm text-ink-muted">
          이어서 풀 문제:{" "}
          <Link href={`/problems/${unsolved.problemId}`} className="font-medium text-brand hover:underline">
            {unsolved.problemTitle}
          </Link>
        </p>
      ) : null}

      <div className="mt-6 flex flex-wrap justify-center gap-2 lg:justify-start">
        <Link href="/problems">
          <Button>문제 풀러 가기</Button>
        </Link>
        <Link href="/submissions">
          <Button variant="secondary">내 제출 보기</Button>
        </Link>
      </div>
    </HeroFrame>
  );
}

/**
 * 인사말.
 *
 * **스트릭이 0 일 때 "0일째" 라고 적지 않는다.** 숫자가 0 인 것을 굳이 보여주면
 * 돌아온 사람을 나무라는 말이 된다. 이어 온 날이 있을 때만 그 숫자를 자랑한다.
 */
function greeting(nickname: string, streak: number | null): { headline: string; note: string } {
  const name = nickname || "코더";

  if (streak === null) {
    return {
      headline: `${name}님, 다시 오셨네요`,
      note: "오늘 한 문제로 이어 가 보세요.",
    };
  }
  if (streak > 0) {
    return {
      headline: `${streak}일째 이어 가는 중`,
      note: `${name}님, 오늘도 한 문제 풀면 기록이 이어집니다.`,
    };
  }
  return {
    headline: `${name}님, 오늘 한 문제 어떠세요?`,
    note: "오늘 한 문제를 풀면 연속 기록이 다시 시작됩니다.",
  };
}
