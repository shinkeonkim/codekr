"use client";

import { ActivityGraph, activityApi } from "@/entities/activity";
import type { ActivityResponse } from "@/entities/activity";
import { TIER_BADGE_CLASSES, TIER_LABELS } from "@/entities/problem";
import { SkillTierBadge } from "@/entities/ranking";
import { Avatar, userApi } from "@/entities/user";
import type { UserProfile } from "@/entities/user";
import { useAuth } from "@/features/auth";
import { SubmissionExplorer } from "@/features/submission-explorer";
import { ApiError } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Card, CardTitle, EmptyState } from "@/shared/ui";
import Link from "next/link";
import { use, useEffect, useState } from "react";
import { SolvedByTagView } from "./SolvedByTagView";

/**
 * 프로필 (#83, #333).
 *
 * **로그인 없이 열린다.** 전에는 아니었고, 그래서 게시판·랭킹·문제집에 걸린 이름을
 * 비로그인이 누르면 로그인 화면으로 튕겼다 — 누르면 튕기는 링크는 고장으로 보인다.
 */
export function UserProfilePage({ params }: { params: Promise<{ nickname: string }> }) {
  const { nickname } = use(params);
  return <ProfileView nickname={decodeURIComponent(nickname)} />;
}

function ProfileView({ nickname }: { nickname: string }) {
  const { user } = useAuth();
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
        <div className="flex flex-wrap items-center gap-3">
          <Avatar
            nickname={profile.nickname}
            avatarUrl={profile.avatarUrl}
            size="lg"
            colorKey={profile.handle}
          />
          <h1 className="text-2xl font-bold text-ink">{profile.nickname}</h1>
          {/* 실력 티어는 아래 '난이도 분포'의 문제 티어와 다른 개념이다. 뱃지가 그것을 명시한다. */}
          <SkillTierBadge tier={profile.skillTier} />
        </div>
        <p className="mt-1 text-xs text-ink-muted">{formatDateTime(profile.joinedAt)} 가입</p>
        {/*
          소개 문구 (#310). **안 썼으면 자리 자체가 없다** — 빈 칸이 남으면 "안 쓴
          사람" 이 아니라 "고장 난 화면" 으로 보인다.

          `whitespace-pre-line` 인 이유: 마크다운이 아니라 줄바꿈만 살리기로 했다.
          제목·목록·이미지가 들어오면 프로필 상단의 모양이 사람마다 달라진다.
        */}
        {profile.bio ? (
          <p className="mt-3 max-w-prose whitespace-pre-line text-sm text-ink">{profile.bio}</p>
        ) : null}
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
      {/* 난이도 분포가 '얼마나 어려운 것' 이면, 이것은 '무엇을' 이다 (#232). */}
      <SolvedByTagView solvedByTag={profile.solvedByTag} />

      {/*
        이 사람이 만든 공개 문제집 (#209).

        **없으면 자리 자체가 없다** — 대부분은 문제집을 만들지 않는다. 빈 상자를 두면
        프로필이 "비어 있는 화면" 으로 보인다.
      */}
      {profile.collections.length > 0 ? (
        <Card className="space-y-2.5 p-5">
          <CardTitle>만든 문제집</CardTitle>
          <ul className="space-y-1.5">
            {profile.collections.map((collection) => (
              <li key={collection.id} className="flex flex-wrap items-center gap-2 text-sm">
                <Link href={`/collections/${collection.id}`} className="text-brand hover:underline">
                  {collection.name}
                </Link>
                <span className="text-xs text-ink-muted">{collection.problemCount}문제</span>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      <section className="space-y-3">
        <CardTitle>최근 제출</CardTitle>
        {/*
          **여기가 공개와 비공개의 경계다** (#333).

          위쪽은 전부 센 숫자라 로그인 없이 보인다. 이 목록은 어떤 문제를 어떤 결과로
          냈는지 한 줄씩 보여 주는 것이고, 전체 제출 목록(#34)이 로그인을 요구한다 —
          여기만 열면 그것이 우회로가 된다.

          비로그인에게 **빈 목록이나 오류를 보이지 않는다.** 그러면 "제출이 없는 사람"
          으로 읽힌다. 왜 안 보이는지 한 줄로 말한다.
        */}
        {user ? (
          <SubmissionExplorer
            fixedNickname={profile.nickname}
            emptyMessage="아직 제출한 코드가 없습니다."
          />
        ) : (
          <Card className="p-5 text-sm text-ink-muted">
            제출 내역은 로그인하면 볼 수 있습니다.{" "}
            <Link href="/login" className="text-brand hover:underline">
              로그인
            </Link>
          </Card>
        )}
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
      <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-border">
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
      <CardTitle>뱃지</CardTitle>
      <ul className="flex flex-wrap gap-2">
        {profile.badges.map((badge) => (
          <li
            key={badge.code}
            title={badge.description}
            className="rounded-full border border-border px-3 py-1 text-xs text-ink"
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
      <CardTitle>푼 문제의 난이도 분포</CardTitle>
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
