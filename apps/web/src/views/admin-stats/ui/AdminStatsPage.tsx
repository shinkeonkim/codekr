"use client";

import { request } from "@/shared/api";
import { BarChart, Card, CardTitle, EmptyState, LineChart } from "@/shared/ui";
import { useEffect, useState } from "react";

interface DayCount {
  day: string;
  total: number;
  accepted: number;
}

interface NamedCount {
  name: string;
  total: number;
}

interface Overview {
  days: number;
  recentDays: number;
  submissions: DayCount[];
  signups: DayCount[];
  verdicts: NamedCount[];
  runtimes: NamedCount[];
  problemKinds: NamedCount[];
}

/** 판정 이름을 사람 말로. 서버 값을 그대로 보이면 화면마다 다른 말이 된다. */
const VERDICT_LABELS: Record<string, string> = {
  ACCEPTED: "맞았습니다",
  WRONG_ANSWER: "틀렸습니다",
  TIME_LIMIT_EXCEEDED: "시간 초과",
  MEMORY_LIMIT_EXCEEDED: "메모리 초과",
  OUTPUT_LIMIT_EXCEEDED: "출력 초과",
  RUNTIME_ERROR: "런타임 에러",
  COMPILE_ERROR: "컴파일 에러",
  SYSTEM_ERROR: "채점 실패 (우리 잘못)",
};

/**
 * 어드민 통계 대시보드 (#550).
 *
 * **목록만 있고 추세가 없었다.** 어드민이 아는 것은 회원 목록·문제 목록·큐 화면(#431)
 * 뿐이라, "요즘 사람들이 얼마나 내는가" 를 보려면 DB 를 열어야 했다.
 *
 * 고르는 기준은 **"보고 나서 할 일이 달라지는가"** 다. 그래서 다섯이다.
 */
export function AdminStatsPage() {
  const [data, setData] = useState<Overview | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    request<Overview>("/api/v1/admin/stats", { auth: true })
      .then(setData)
      .catch(() => setError("통계를 불러오지 못했습니다."));
  }, []);

  if (error) return <EmptyState title={error} />;
  if (!data) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  const days = (rows: DayCount[], pick: (row: DayCount) => number) =>
    rows.map((row) => ({ label: row.day.slice(5), value: pick(row) }));

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-ink">통계</h1>

      <Card className="space-y-2 p-5">
        <div>
          <CardTitle>제출 ({data.days}일)</CardTitle>
          <p className="mt-1 text-xs text-ink-muted">
            사람들이 쓰고 있는가. 갑자기 줄면 무엇이 막힌 것이다.
          </p>
        </div>
        <LineChart points={days(data.submissions, (row) => row.total)} label={`최근 ${data.days}일 제출 수`} />
        {/* 정답만 따로 본다 — 제출은 느는데 정답이 안 늘면 문제가 어려워졌거나 채점이 아프다. */}
        <LineChart
          points={days(data.submissions, (row) => row.accepted)}
          label={`최근 ${data.days}일 정답 수`}
          tone="text-ok"
        />
      </Card>

      <Card className="space-y-2 p-5">
        <div>
          <CardTitle>가입 ({data.days}일)</CardTitle>
          <p className="mt-1 text-xs text-ink-muted">들어오고 있는가.</p>
        </div>
        <LineChart points={days(data.signups, (row) => row.total)} label={`최근 ${data.days}일 가입 수`} />
      </Card>

      <Card className="space-y-3 p-5">
        <div>
          <CardTitle>판정 분포 (최근 {data.recentDays}일)</CardTitle>
          <p className="mt-1 text-xs text-ink-muted">
            <strong className="text-ink">채점 실패</strong>가 눈에 띄면 우리가 아픈 것이다 — 그동안
            사용자는 자기 코드를 의심하며 시간을 쓴다.
          </p>
        </div>
        <BarChart
          label="판정 분포"
          items={data.verdicts.map((row) => ({
            label: VERDICT_LABELS[row.name] ?? row.name,
            value: row.total,
          }))}
        />
      </Card>

      <div className="grid gap-4 md:grid-cols-2">
        <Card className="space-y-3 p-5">
          <div>
            <CardTitle>언어 (최근 {data.recentDays}일)</CardTitle>
            <p className="mt-1 text-xs text-ink-muted">런타임을 늘릴지 줄일지의 근거다.</p>
          </div>
          <BarChart label="언어별 제출" items={data.runtimes.map((row) => ({ label: row.name, value: row.total }))} />
        </Card>

        <Card className="space-y-3 p-5">
          <div>
            <CardTitle>문제 유형</CardTitle>
            <p className="mt-1 text-xs text-ink-muted">공개된 문제만 센다. 지금 우리가 가진 것이다.</p>
          </div>
          <BarChart
            label="유형별 문제 수"
            items={data.problemKinds.map((row) => ({ label: row.name, value: row.total }))}
          />
        </Card>
      </div>
    </div>
  );
}
