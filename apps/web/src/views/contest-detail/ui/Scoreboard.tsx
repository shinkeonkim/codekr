"use client";

import type { Scoreboard as ScoreboardData, ScoreboardCell } from "@/entities/contest";
import { useAuth } from "@/features/auth";
import { Alert, Card, CheckboxField } from "@/shared/ui";

/**
 * 대회 순위표 (#63).
 *
 * **본인 행을 강조한다.** 100명 중 47등이면 스크롤해서 찾아야 하는데, 대회 중에
 * 그 시간을 쓰게 하면 안 된다 (#86).
 */
export function Scoreboard({
  data,
  actual,
  canSeeActual,
  onToggleActual,
}: {
  data: ScoreboardData;
  actual: boolean;
  canSeeActual: boolean;
  onToggleActual: (next: boolean) => void;
}) {
  const { user } = useAuth();

  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="text-sm font-semibold text-ink">순위표</h2>
        {canSeeActual ? (
          <CheckboxField label="실제 순위 보기" checked={actual} onCheckedChange={onToggleActual} />
        ) : null}
      </div>

      {/* 모르고 보면 순위가 멈춘 것이 오류로 보인다. 크게 알린다. */}
      {data.frozen && !actual ? (
        <Alert>🔒 순위가 동결되었습니다. 동결 이후의 결과는 대회가 끝난 뒤에 공개됩니다.</Alert>
      ) : null}
      {data.rejudgeInProgress ? (
        <Alert>재채점이 진행 중입니다 — 순위가 바뀔 수 있습니다.</Alert>
      ) : null}

      <Card
        className={`overflow-x-auto p-0 ${actual ? "border-warn/50 bg-warn/5" : ""}`}
        // 어드민이 어느 쪽을 보고 있는지 헷갈리면 잘못된 판단을 한다. 배경으로 구분한다.
      >
        <table className="w-full min-w-[640px] text-sm">
          <thead>
            <tr className="border-b border-border text-xs text-ink-muted">
              <th className="w-12 px-3 py-2 text-left">순위</th>
              <th className="px-3 py-2 text-left">참가자</th>
              <th className="w-16 px-3 py-2 text-right">총점</th>
              {data.problems.map((problem) => (
                <th key={problem.slug} className="w-20 px-2 py-2 text-center">
                  <span className={problem.excluded ? "line-through" : ""}>{problem.label}</span>
                  <span className="block font-normal">{problem.solvedCount}명</span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data.rows.map((row) => {
              const mine = user?.nickname === row.nickname;
              return (
                <tr
                  key={row.nickname}
                  className={`border-b border-border/60 last:border-0 ${mine ? "bg-brand/10 font-medium" : ""}`}
                >
                  <td className="px-3 py-2 tabular-nums text-ink-muted">{row.rank}</td>
                  <td className="px-3 py-2 text-ink">{row.nickname}</td>
                  <td className="px-3 py-2 text-right font-semibold tabular-nums text-ink">
                    {row.totalScore}
                  </td>
                  {row.cells.map((cell, index) => (
                    <td key={data.problems[index]?.slug ?? index} className="px-2 py-2 text-center">
                      <CellView cell={cell} />
                    </td>
                  ))}
                </tr>
              );
            })}
          </tbody>
        </table>
      </Card>
    </section>
  );
}

/**
 * 칸 하나.
 *
 * `… 2회` 가 동결 이후의 시도다 — **시도했다는 사실은 감추지 않는다.** 감추면
 * 순위표가 대회 후반에 아무 정보도 주지 않는다 (#86).
 */
function CellView({ cell }: { cell: ScoreboardCell }) {
  if (cell.solved) {
    return (
      <span className="text-ok">
        ✔<span className="ml-0.5 text-xs tabular-nums">{cell.solvedMinutes}′</span>
      </span>
    );
  }
  if (cell.pending > 0) {
    return <span className="text-xs text-ink-muted">… {cell.pending}회</span>;
  }
  if (cell.attempts > 0) {
    return <span className="text-xs text-danger">✕ {cell.attempts}회</span>;
  }
  return <span className="text-xs text-ink-muted">—</span>;
}
