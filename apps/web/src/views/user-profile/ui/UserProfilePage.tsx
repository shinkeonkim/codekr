"use client";

import { ActivityGraph, activityApi } from "@/entities/activity";
import type { ActivityResponse } from "@/entities/activity";
import { TIER_BADGE_CLASSES, TIER_LABELS } from "@/entities/problem";
import { SkillTierBadge } from "@/entities/ranking";
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
  const [activity, setActivity] = useState<ActivityResponse | null>(null);
  const [year, setYear] = useState(() => new Date().getFullYear());

  useEffect(() => {
    userApi
      .profile(nickname)
      .then(setProfile)
      .catch((caught) =>
        setError(caught instanceof ApiError ? caught.message : "프로필을 불러오지 못했습니다."),
      );
  }, [nickname]);

  useEffect(() => {
    activityApi
      .ofUser(nickname, { year })
      .then(setActivity)
      .catch(() => setActivity(null));
  }, [nickname, year]);

  if (error) return <EmptyState title={error} />;
  if (!profile) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  return (
    <div className="space-y-5">
      <header>
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-bold text-ink">{profile.nickname}</h1>
          {/* 실력 티어는 아래 '난이도 분포'의 문제 티어와 다른 개념이다. 뱃지가 그것을 명시한다. */}
          <SkillTierBadge tier={profile.skillTier} />
        </div>
        <p className="mt-1 text-xs text-ink-muted">{formatDateTime(profile.joinedAt)} 가입</p>
      </header>

      <Card className="grid grid-cols-2 gap-4 p-5 sm:grid-cols-4">
        <Stat label="실력 점수" value={profile.score.toLocaleString()} />
        <Stat label="랭킹" value={profile.rank ? `${profile.rank}위` : "—"} />
        <Stat label="푼 문제" value={profile.solvedCount} />
        <Stat label="제출" value={profile.submissionCount} />
        <Stat label="현재 스트릭" value={`${profile.currentStreak}일`} />
        <Stat label="최장 스트릭" value={`${profile.longestStreak}일`} />
      </Card>

      <NextTier profile={profile} />
      <Badges profile={profile} />

      {/*
        스트릭 숫자만으로는 꾸준한 사람인지 최근에 몰아친 사람인지 구분되지 않는다.
        그것을 보여주는 것이 캘린더의 역할이다 (#117).
      */}
      {activity ? <ActivityGraph activity={activity} year={year} onYearChange={setYear} /> : null}

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

/**
 * 다음 티어까지 남은 점수 (#58).
 *
 * 현재 티어만 보여주면 숫자가 목표가 되지 못한다. 얼마가 남았는지가 다음 한 문제를 부른다.
 */
function NextTier({ profile }: { profile: UserProfile }) {
  const tier = profile.skillTier;
  if (!tier?.nextLevelScore) return null;

  const remaining = tier.nextLevelScore - profile.score;
  if (remaining <= 0) return null;

  return (
    <Card className="p-5">
      <p className="text-xs text-ink-muted">
        다음 실력 티어까지 <span className="font-semibold text-ink">{remaining.toLocaleString()}점</span>
      </p>
      <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-line">
        <div
          className="h-full rounded-full bg-brand"
          style={{ width: `${Math.min(100, (profile.score / tier.nextLevelScore) * 100)}%` }}
        />
      </div>
    </Card>
  );
}

/** 뱃지 (#58). 행동 기반으로만 준다 — 점수 기반 뱃지는 실력 티어와 같은 말이다. */
function Badges({ profile }: { profile: UserProfile }) {
  if (profile.badges.length === 0) return null;

  return (
    <Card className="space-y-2.5 p-5">
      <h2 className="text-sm font-semibold text-ink">뱃지</h2>
      <ul className="flex flex-wrap gap-2">
        {profile.badges.map((badge) => (
          <li
            key={badge.code}
            title={badge.description}
            className="rounded-full border border-line px-3 py-1 text-xs text-ink"
          >
            {badge.label}
          </li>
        ))}
      </ul>
    </Card>
  );
}

/** 푼 문제의 난이도 분포. 무엇을 주로 푸는지가 숫자 하나보다 많은 것을 말한다. */
function SolvedByTierView({ profile }: { profile: UserProfile }) {
  if (profile.solvedByTier.length === 0) return null;
  const max = Math.max(...profile.solvedByTier.map((it) => it.solvedCount));

  return (
    <Card className="space-y-2.5 p-5">
      {/* '실력 티어'와 헷갈리지 않게 무엇의 난이도인지 밝힌다. */}
      <h2 className="text-sm font-semibold text-ink">푼 문제의 난이도 분포</h2>
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
