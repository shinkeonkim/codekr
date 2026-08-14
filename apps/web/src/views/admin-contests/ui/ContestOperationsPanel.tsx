"use client";

import { adminContestApi } from "@/entities/contest";
import type { AdminContest } from "@/entities/contest";
import { ApiError } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Alert, Button, Card, CardTitle, ConfirmDialog, useToast } from "@/shared/ui";
import { useRouter } from "next/navigation";
import { useState } from "react";

/**
 * 대회 운영자가 대회 중·후에 하는 일들 (#63, #544).
 *
 * **#63 은 `[api][web]` 인데 web 이 안 들어갔다.** 그래서 프리즈(#86)를 넣어 놓고
 * **푸는 경로가 화면에 없었고**, 대회 중에 문제가 잘못돼도 뺄 수가 없었다 —
 * 손을 쓸 수 있는 유일한 순간에 화면이 없는 상태였다.
 */
export function ContestOperationsPanel({
  contest,
  onChanged,
}: {
  contest: AdminContest;
  onChanged: (next: AdminContest) => void;
}) {
  const router = useRouter();
  const toast = useToast();
  const [busy, setBusy] = useState(false);

  const run = async (action: () => Promise<AdminContest>, done: string, fallback: string) => {
    setBusy(true);
    try {
      onChanged(await action());
      toast.success(done);
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : fallback);
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    setBusy(true);
    try {
      await adminContestApi.remove(contest.id);
      toast.success("대회를 지웠습니다.");
      router.push("/admin/contests");
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "지우지 못했습니다.");
      setBusy(false);
    }
  };

  // **시계를 화면이 읽지 않는다.** 서버가 이미 단계를 계산해 준다 — 두 곳이 시각을
  // 따로 재면 경계에서 갈린다 (그리고 렌더 중 Date.now() 는 순수하지 않다).
  const ended = contest.phase === "ENDED" || contest.phase === "ARCHIVED";
  // 서버가 DRAFT 만 지운다. 열어 두고 누를 때마다 거절당하게 두면 그건 화면 잘못이다.
  const deletable = contest.status === "DRAFT";

  return (
    <Card className="space-y-4 p-5">
      <CardTitle>운영</CardTitle>

      <section className="space-y-2">
        <p className="text-sm font-medium text-ink">순위표</p>
        {contest.unfrozenAt ? (
          <p className="text-xs text-ok">{formatDateTime(contest.unfrozenAt)} 에 최종 순위를 공개했습니다.</p>
        ) : contest.frozen ? (
          <p className="text-xs text-warn">
            지금 동결 중입니다. <span className="text-ink">끝난 뒤 여기서 풀지 않으면 가려진 채로 남습니다.</span>
          </p>
        ) : (
          <p className="text-xs text-ink-muted">동결 중이 아닙니다.</p>
        )}
        {!contest.unfrozenAt ? (
          <ConfirmDialog
            title="최종 순위를 공개합니다"
            description="동결이 풀리고 모든 참가자에게 최종 순위가 보입니다. 다시 얼릴 수는 없습니다."
            confirmLabel="공개하기"
            trigger={
              <Button disabled={busy || !ended} variant="secondary">
                최종 순위 공개
              </Button>
            }
            onConfirm={() =>
              run(() => adminContestApi.unfreeze(contest.id), "최종 순위를 공개했습니다.", "공개하지 못했습니다.")
            }
          />
        ) : null}
        {!ended && !contest.unfrozenAt ? (
          <p className="text-xs text-ink-muted">대회가 끝난 뒤에 공개할 수 있습니다.</p>
        ) : null}
      </section>

      <section className="space-y-2 border-t border-border pt-3">
        <p className="text-sm font-medium text-ink">문제</p>
        {contest.problems.length === 0 ? (
          <p className="text-xs text-ink-muted">붙인 문제가 없습니다.</p>
        ) : (
          <ul className="space-y-1">
            {contest.problems.map((problem) => (
              <li key={problem.problemId} className="flex flex-wrap items-center gap-2 text-sm">
                <span className="w-6 text-ink-muted">{problem.label}</span>
                <span className={`flex-1 truncate ${problem.excluded ? "text-ink-muted line-through" : "text-ink"}`}>
                  {problem.title}
                </span>
                <Button
                  variant="secondary"
                  className="px-2 py-1 text-xs"
                  disabled={busy}
                  onClick={() =>
                    run(
                      () => adminContestApi.excludeProblem(contest.id, problem.problemId, !problem.excluded),
                      problem.excluded ? "다시 넣었습니다." : "제외했습니다.",
                      "바꾸지 못했습니다.",
                    )
                  }
                >
                  {problem.excluded ? "다시 넣기" : "제외"}
                </Button>
              </li>
            ))}
          </ul>
        )}
        <p className="text-xs text-ink-muted">
          제외한 문제는 순위 계산에서 빠집니다. 데이터가 틀렸거나 답이 샜을 때 씁니다.
        </p>
      </section>

      <section className="space-y-2 border-t border-border pt-3">
        <p className="text-sm font-medium text-ink">대회 삭제</p>
        {deletable ? (
          <ConfirmDialog
            title="대회를 지웁니다"
            description="준비 중인 대회만 지울 수 있습니다. 목록에서 사라집니다."
            confirmLabel="지우기"
            trigger={
              <Button variant="danger" disabled={busy}>
                대회 삭제
              </Button>
            }
            onConfirm={remove}
          />
        ) : (
          <Alert tone="muted">
            공개한 대회는 지울 수 없습니다. 제출 이력이 딸려 있기 때문입니다 — 취소를 쓰십시오.
          </Alert>
        )}
      </section>
    </Card>
  );
}
