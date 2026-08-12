"use client";

import { activityApi } from "@/entities/activity";
import { rankingApi } from "@/entities/ranking";
import { RejudgePanel } from "@/features/rejudge";
import { TagAdminPanel } from "@/features/tag-admin";
import { useToast } from "@/shared/ui";
import { OperationCard } from "./OperationCard";
import type { Operation } from "./OperationCard";

/**
 * 운영 작업 (#180).
 *
 * **API 만 있고 화면이 없으면 있는 줄도 모른다.** 특히 랭킹 재계산은 기능을 붙인 직후
 * 반드시 한 번 불러야 하는 것인데(#177), 그 사실이 화면 어디에도 없었다.
 *
 * 작업을 **배열로 선언한다** — 새 작업이 늘 때 한 줄 추가로 끝나야 여기가 다시 비어
 * 있는 상태로 돌아가지 않는다.
 */
export function AdminOperationsPage() {
  const toast = useToast();

  const operations: Operation[] = [
    {
      key: "ranking-all",
      label: "랭킹 전체 재계산",
      description:
        "맞힌 제출이 있는 모든 회원의 점수·실력 티어·뱃지를 제출 기록에서 다시 만듭니다. 랭킹을 처음 켠 뒤에는 한 번 눌러야 그 전의 제출이 반영됩니다.",
      // 사용자가 많으면 오래 걸린다. 되돌릴 수는 있지만(다시 누르면 된다) 도중에
      // 멈출 수 없으므로 누르기 전에 확인을 받는다.
      confirm: "모든 회원의 랭킹을 다시 계산합니다. 회원이 많으면 시간이 걸립니다. 진행할까요?",
      run: async () => {
        const result = await rankingApi.recomputeAll();
        return `${result.users.toLocaleString("ko-KR")}명을 다시 계산했습니다.`;
      },
    },
    {
      key: "ranking-user",
      label: "랭킹 재계산 (회원 지정)",
      description: "한 회원만 다시 계산합니다. 점수가 어긋났다는 제보를 확인할 때 씁니다.",
      argument: { label: "회원 ID", placeholder: "예: 42" },
      run: async (userId) => {
        const result = await rankingApi.recomputeUser(userId);
        return `점수 ${result.score.toLocaleString("ko-KR")}점 · 맞힌 문제 ${result.solvedCount}개`;
      },
    },
    {
      key: "activity-user",
      label: "활동 집계 재계산 (회원 지정)",
      description: "프로필 활동 그래프와 스트릭을 제출 기록에서 다시 만듭니다.",
      argument: { label: "회원 ID", placeholder: "예: 42" },
      run: async (userId) => {
        const result = await activityApi.recompute(userId);
        return `${result.days}일치를 다시 계산했습니다.`;
      },
    },
  ];

  return (
    <div className="space-y-5">
      <header>
        <h1 className="text-2xl font-bold text-ink">운영 작업</h1>
        <p className="mt-1 text-sm text-ink-muted">
          저장해 둔 집계를 원자료에서 다시 만듭니다. 재채점을 뺀 나머지는 여러 번 눌러도
          결과가 같습니다.
        </p>
      </header>

      <div className="grid gap-3 lg:grid-cols-2">
        {operations.map((operation) => (
          <OperationCard
            key={operation.key}
            operation={operation}
            onError={toast.error}
          />
        ))}

        {/*
          재채점만 카드 밖에 따로 있는 이유 (#219): 다른 작업은 "인자 하나 + 실행" 인데
          이것은 문제를 고르고, 대상 수를 보고, 이유를 적는 절차가 필요하다. `Operation`
          배열에 억지로 끼우면 그 배열이 무엇이든 담는 것이 되어 형태가 사라진다.
        */}
        <RejudgePanel onError={toast.error} />

        {/* 태그는 어드민만 만든다 (#232). 만드는 자리도 어드민에 있어야 한다. */}
        <TagAdminPanel />
      </div>
    </div>
  );
}
