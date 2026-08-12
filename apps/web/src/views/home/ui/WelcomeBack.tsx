"use client";

import { activityApi } from "@/entities/activity";
import { useAuth } from "@/features/auth";
import { BrandCharacter, Button, Card } from "@/shared/ui";
import Link from "next/link";
import { useEffect, useState } from "react";
import type { BrandCharacterName } from "@/shared/ui";

/**
 * 이미 쓰는 사람의 첫 화면 (#231, #261).
 *
 * 소개는 이 사람에게 지나가야 할 벽이다. 대신 **오늘 무엇을 할지**를 정해 준다 —
 * 이어 온 날이 며칠인지, 지금 바로 누를 곳이 어디인지.
 *
 * 캐릭터가 상태에 따라 바뀐다. 그림이 장식이 아니라 **상태를 한 번 더 말하는 자리**가
 * 되게 하려는 것이다 — 이어 오는 중이면 축하하는 얼굴, 오늘 아직이면 기다리는 얼굴.
 */
export function WelcomeBack() {
  const { user } = useAuth();
  const [streak, setStreak] = useState<number | null>(null);

  useEffect(() => {
    activityApi
      .mine()
      .then((activity) => setStreak(activity.currentStreak))
      .catch(() => setStreak(null));
  }, []);

  const { headline, note, character } = greeting(user?.nickname ?? "", streak);

  return (
    <Card className="flex flex-col items-center gap-6 p-6 sm:flex-row sm:p-8">
      <BrandCharacter name={character} size={200} className="shrink-0" />

      <div className="min-w-0 flex-1 text-center sm:text-left">
        <h1 className="text-2xl font-bold tracking-tight text-ink sm:text-3xl">{headline}</h1>
        <p className="mt-2 text-sm text-ink-muted">{note}</p>

        <div className="mt-5 flex flex-wrap justify-center gap-2 sm:justify-start">
          <Link href="/problems">
            <Button>문제 풀러 가기</Button>
          </Link>
          <Link href="/submissions">
            <Button variant="secondary">내 제출 보기</Button>
          </Link>
        </div>
      </div>
    </Card>
  );
}

/**
 * 인사말과 캐릭터를 함께 고른다.
 *
 * **스트릭이 0 일 때 "0일째" 라고 적지 않는다.** 숫자가 0 인 것을 굳이 보여주면
 * 돌아온 사람을 나무라는 말이 된다. 이어 온 날이 있을 때만 그 숫자를 자랑한다.
 */
function greeting(
  nickname: string,
  streak: number | null,
): { headline: string; note: string; character: BrandCharacterName } {
  const name = nickname || "코더";

  if (streak === null) {
    return {
      headline: `${name}님, 다시 오셨네요`,
      note: "오늘 한 문제로 이어 가 보세요.",
      character: "laptop",
    };
  }
  if (streak > 0) {
    return {
      headline: `${streak}일째 이어 가는 중`,
      note: `${name}님, 오늘도 한 문제 풀면 기록이 이어집니다.`,
      character: "celebration",
    };
  }
  return {
    headline: `${name}님, 오늘 한 문제 어떠세요?`,
    note: "오늘 한 문제를 풀면 연속 기록이 다시 시작됩니다.",
    character: "thinking",
  };
}
