"use client";

import { TIER_BADGE_CLASSES, TIER_LABELS } from "@/entities/problem";
import { userApi } from "@/entities/user";
import type { UserProfile } from "@/entities/user";
import { RequireAuth } from "@/features/auth";
import { SubmissionExplorer } from "@/features/submission-explorer";
import { ApiError } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Card, EmptyState } from "@/shared/ui";
import { use, useEffect, useState } from "react";

export function UserProfilePage({ params }: { params: Promise<{ nickname: string }> }) {
  const { nickname } = use(params);
  return (
    <RequireAuth>
      <ProfileView nickname={decodeURIComponent(nickname)} />
    </RequireAuth>
  );
}

function ProfileView({ nickname }: { nickname: string }) {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    userApi
      .profile(nickname)
      .then(setProfile)
      .catch((caught) =>
        setError(caught instanceof ApiError ? caught.message : "프로필을 불러오지 못했습니다."),
      );
  }, [nickname]);

  if (error) return <EmptyState title={error} />;
  if (!profile) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  return (
    <div className="space-y-5">
      <header>
        <h1 className="text-2xl font-bold text-ink">{profile.nickname}</h1>
        <p className="mt-1 text-xs text-ink-muted">{formatDateTime(profile.joinedAt)} 가입</p>
      </header>

      <Card className="grid grid-cols-2 gap-4 p-5 sm:grid-cols-4">
        <Stat label="푼 문제" value={profile.solvedCount} />
        <Stat label="제출" value={profile.submissionCount} />
        <Stat label="현재 스트릭" value={`${profile.currentStreak}일`} />
        <Stat label="최장 스트릭" value={`${profile.longestStreak}일`} />
      </Card>

      <SolvedByTierView profile={profile} />

      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-ink">최근 제출</h2>
        {/* 전체 제출 목록을 이 사람으로 좁혀 그대로 재사용한다. 같은 화면을 두 벌 만들지 않는다. */}
        <SubmissionExplorer
          fixedNickname={profile.nickname}
          emptyMessage="아직 제출한 코드가 없습니다."
        />
      </section>
    </div>
  );
}

/** 푼 문제의 난이도 분포. 무엇을 주로 푸는지가 숫자 하나보다 많은 것을 말한다. */
function SolvedByTierView({ profile }: { profile: UserProfile }) {
  if (profile.solvedByTier.length === 0) return null;
  const max = Math.max(...profile.solvedByTier.map((it) => it.solvedCount));

  return (
    <Card className="space-y-2.5 p-5">
      <h2 className="text-sm font-semibold text-ink">난이도 분포</h2>
      <ul className="space-y-1.5">
        {profile.solvedByTier.map((entry) => (
          <li key={entry.tier} className="flex items-center gap-3">
            <span
              className={`inline-flex w-20 shrink-0 justify-center rounded-full border px-2 py-0.5 text-xs font-medium ${
                TIER_BADGE_CLASSES[entry.tier]
              }`}
            >
              {TIER_LABELS[entry.tier]}
            </span>
            <span className="h-2 rounded-full bg-brand/60" style={{ width: `${(entry.solvedCount / max) * 60}%` }} />
            <span className="text-xs text-ink-muted">{entry.solvedCount}문제</span>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div>
      <p className="text-xs text-ink-muted">{label}</p>
      <p className="mt-0.5 text-lg font-semibold text-ink">{value}</p>
    </div>
  );
}
