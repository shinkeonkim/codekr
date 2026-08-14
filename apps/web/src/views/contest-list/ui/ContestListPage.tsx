"use client";

import { contestApi } from "@/entities/contest";
import type { ContestSummary } from "@/entities/contest";
import { useAuth } from "@/features/auth";
import { formatDateTime } from "@/shared/lib";
import { Badge, Card, EmptyState, Pagination } from "@/shared/ui";
import { PhaseBadge } from "@/entities/contest";
import Link from "next/link";
import { useEffect, useState } from "react";

const PAGE_SIZE = 20;

type Tab = "all" | "mine";

/**
 * 대회 목록 (#61).
 *
 * ## 왜 탭이 있는가 (#546)
 *
 * 전체 목록은 그중 무엇에 내가 들어가 있는지를 알려주지 않는다. 그래서 참가자는
 * **자기가 신청했는지를 기억으로 관리**했다 — 상세를 하나씩 열어 버튼이 "신청" 인지
 * "신청함" 인지 봐야 했다.
 *
 * 승인이 붙은 뒤(#466)로는 더 나쁘다. **대기 중이라는 것을 볼 자리가 어디에도 없었다.**
 * 그리고 비공개 대회(#465)는 목록에 안 뜨므로 **주소를 잃으면 돌아올 길이 없었다** —
 * 이 탭이 그 유일한 길이다.
 */
export function ContestListPage() {
  const { user } = useAuth();
  const [tab, setTab] = useState<Tab>("all");
  const [contests, setContests] = useState<ContestSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);

  // **효과 안에서 곧바로 setState 하지 않는다** — 렌더가 연쇄한다.
  // 불러오기를 시작시키는 곳(탭·쪽 바꾸기)에서 미리 켠다.
  useEffect(() => {
    const load = tab === "mine" ? contestApi.registered(page, PAGE_SIZE) : contestApi.list(page, PAGE_SIZE);
    load
      .then((result) => {
        setContests(result.content);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
      })
      .catch(() => setContests([]))
      .finally(() => setLoading(false));
  }, [page, tab]);

  const select = (next: Tab) => {
    if (next === tab) return;
    setLoading(true);
    // 탭을 바꾸면 첫 쪽으로 간다 — 3쪽을 보다 옮기면 없는 쪽을 보게 된다.
    setPage(0);
    setTab(next);
  };

  const changePage = (next: number) => {
    setLoading(true);
    setPage(next);
  };

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold text-ink">대회</h1>
        {/* 로그인하지 않았으면 내 대회가 있을 수 없다. 눌러 봐야 401 이다. */}
        {user ? (
          <nav className="ml-auto flex gap-1 text-sm">
            <TabButton current={tab} value="all" onSelect={select}>
              전체
            </TabButton>
            <TabButton current={tab} value="mine" onSelect={select}>
              내 대회
            </TabButton>
          </nav>
        ) : null}
      </header>

      {loading ? (
        <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>
      ) : contests.length === 0 ? (
        <EmptyState
          mascot="award"
          title={tab === "mine" ? "신청한 대회가 없습니다." : "아직 열린 대회가 없습니다."}
          description={tab === "mine" ? "대회에 신청하면 여기에서 다시 찾을 수 있습니다." : undefined}
        />
      ) : (
        <div className="space-y-2">
          {contests.map((contest) => (
            <ContestCard key={contest.slug} contest={contest} />
          ))}
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onChange={changePage} />
    </div>
  );
}

function TabButton({
  current,
  value,
  onSelect,
  children,
}: {
  current: Tab;
  value: Tab;
  onSelect: (tab: Tab) => void;
  children: string;
}) {
  const active = current === value;
  return (
    <button
      type="button"
      onClick={() => onSelect(value)}
      aria-current={active ? "page" : undefined}
      className={`rounded-full px-3 py-1 transition ${
        active ? "bg-surface-muted font-medium text-ink" : "text-ink-muted hover:text-ink"
      }`}
    >
      {children}
    </button>
  );
}

function ContestCard({ contest }: { contest: ContestSummary }) {
  return (
    <Link href={`/contests/${encodeURIComponent(contest.slug)}`} className="block">
      <Card className="flex flex-wrap items-center gap-3 p-5 transition hover:border-brand/50">
        <PhaseBadge contest={contest} />
        {/*
          대기 중이라는 것을 여기서 말한다 (#546). 승인이 필요한 대회에 신청해 놓고
          기다리는 사람은 자기가 기다리는 중인지도 몰랐다.
        */}
        {contest.registrationStatus === "PENDING" ? <Badge tone="warn">승인 대기</Badge> : null}
        <span className="flex-1 truncate font-medium text-ink">{contest.title}</span>
        <span className="text-xs text-ink-muted">참가 {contest.participantCount}명</span>
        <span className="w-full text-xs text-ink-muted sm:w-auto">
          {formatDateTime(contest.startsAt)} — {formatDateTime(contest.endsAt)}
        </span>
      </Card>
    </Link>
  );
}
