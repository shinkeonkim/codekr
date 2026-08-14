"use client";

import { problemStatsApi } from "@/entities/problem-stats";
import type { StatsDrift } from "@/entities/problem-stats";
import { ApiError } from "@/shared/api";
import { Button, Card } from "@/shared/ui";
import Link from "next/link";
import { useState } from "react";

/**
 * 문제 통계가 어긋났는지 본다 (#205, #551).
 *
 * **어긋남은 조용하다.** 화면에는 그럴듯한 숫자가 그대로 나오기 때문에, 볼 자리가
 * 없으면 아무도 모른다. 그리고 그 위에 난이도 투표(#477)와 점수(#194)가 쌓인다.
 *
 * `OperationCard` 에 끼우지 않은 이유는 재채점(#219)과 같다 — 저 카드는
 * "인자 하나 + 실행" 이고, 이것은 **어느 문제가 얼마나 다른지를 보여 주는 것**이
 * 일의 절반이다. 결과가 한 줄로 줄지 않는다.
 */
export function StatsDriftPanel({ onError }: { onError: (message: string) => void }) {
  const [drifts, setDrifts] = useState<StatsDrift[] | null>(null);
  const [busy, setBusy] = useState(false);

  const run = async (action: () => Promise<unknown>, fallback: string) => {
    setBusy(true);
    try {
      await action();
      // 고친 뒤에도 다시 본다 — 고쳐졌다는 것을 눈으로 확인할 수 있어야 한다.
      setDrifts(await problemStatsApi.drift());
    } catch (caught) {
      onError(caught instanceof ApiError ? caught.message : fallback);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="space-y-3 p-5">
      <div>
        <p className="font-medium text-ink">문제 통계 어긋남</p>
        <p className="mt-1 text-xs leading-relaxed text-ink-muted">
          저장해 둔 제출자·정답자 수를 제출 기록과 견줍니다. 채점이 중간에 끊기거나
          제출이 지워지면 어긋나는데, 화면에는 그럴듯한 숫자가 그대로 나오므로
          여기서 보지 않으면 알 수 없습니다.
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Button
          variant="secondary"
          disabled={busy}
          onClick={() => run(() => problemStatsApi.drift(), "어긋남을 확인하지 못했습니다.")}
        >
          {busy ? "확인 중…" : "어긋난 것 보기"}
        </Button>
        <Button
          disabled={busy}
          onClick={() => run(problemStatsApi.recompute, "다시 계산하지 못했습니다.")}
        >
          전부 다시 계산
        </Button>
      </div>

      {drifts !== null ? <DriftList drifts={drifts} /> : null}
    </Card>
  );
}

function DriftList({ drifts }: { drifts: StatsDrift[] }) {
  if (drifts.length === 0) {
    return <p className="border-t border-border pt-3 text-xs text-ok">어긋난 문제가 없습니다.</p>;
  }

  return (
    <div className="space-y-2 border-t border-border pt-3">
      <p className="text-xs text-ink-muted">{drifts.length}개가 어긋났습니다. 저장된 값 → 실제 값</p>
      {/* 문제가 많으면 표가 화면을 밀어낸다 — 안에서만 넘치게 한다 (#484). */}
      <div className="max-h-64 overflow-auto">
        <table className="w-full text-xs">
          <thead className="text-ink-muted">
            <tr>
              <th className="py-1 text-left font-normal">문제</th>
              <th className="py-1 text-right font-normal">제출자</th>
              <th className="py-1 text-right font-normal">정답자</th>
            </tr>
          </thead>
          <tbody>
            {drifts.map((drift) => (
              <tr key={drift.problemId} className="border-t border-border">
                <td className="py-1">
                  <Link href={`/admin/problems/${drift.problemId}/edit`} className="hover:underline">
                    #{drift.problemId}
                  </Link>
                </td>
                <Cell stored={drift.storedSubmitters} actual={drift.actualSubmitters} />
                <Cell stored={drift.storedSolvers} actual={drift.actualSolvers} />
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** 같은 값이면 흐리게 둔다. 어느 칸이 어긋났는지가 눈에 들어와야 한다. */
function Cell({ stored, actual }: { stored: number; actual: number }) {
  const same = stored === actual;
  return (
    <td className={`py-1 text-right tabular-nums ${same ? "text-ink-muted" : "text-danger"}`}>
      {same ? stored : `${stored} → ${actual}`}
    </td>
  );
}
