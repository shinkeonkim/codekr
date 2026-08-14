"use client";

import { activityApi } from "@/entities/activity";
import { DATA_RESET_CONFIRMATION, adminDataApi } from "@/entities/admin-data";
import { rankingApi } from "@/entities/ranking";
import { retentionApi } from "@/entities/retention";
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
      key: "data-reset",
      label: "데이터 초기화",
      description:
        "문제·제출·랭킹·활동·뱃지·대회·문제집을 모두 지우고 문제 번호를 1번부터 다시 시작합니다. " +
        "회원 계정과 게시판 글, 알고리즘 분류는 남습니다. 되돌릴 수 없습니다.",
      confirm:
        "지웁니다: 문제, 제출, 랭킹 점수, 활동 기록, 뱃지, 대회, 문제집, 알림. " +
        "남깁니다: 회원 계정, 게시판 글과 댓글, 알고리즘 분류. 되돌릴 수 없습니다.",
      confirmPhrase: DATA_RESET_CONFIRMATION,
      run: async () => {
        const result = await adminDataApi.reset(DATA_RESET_CONFIRMATION);
        return `${result.clearedTables.length}개 표에서 ${result.clearedRows.toLocaleString("ko-KR")}행을 지웠습니다.`;
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
      key: "retention-cleanup",
      label: "지운 것 정리",
      description:
        "소프트 삭제된 문제·테스트케이스·시작 코드·알림을 실제로 지웁니다. " +
        "새벽 4시에 자동으로도 도는 작업이라, 여기서 부르는 것은 기다리지 않고 결과를 볼 때입니다. " +
        "보관 기간이 지난 것만 지웁니다.",
      // 되돌릴 수 없다. 다만 지우는 대상이 이미 "지워진 것" 이라 문구를 옮겨 적게
      // 하지는 않는다 — 데이터 초기화(#285)와 무게가 다르다.
      confirm: "보관 기간이 지난 소프트 삭제 행을 실제로 지웁니다. 되돌릴 수 없습니다. 진행할까요?",
      run: async () => {
        const report = await retentionApi.cleanup();
        if (report.total === 0) return "지울 것이 없습니다.";
        const parts = [
          report.deletedProblems > 0 ? `문제 ${report.deletedProblems}` : null,
          report.deletedTestcases > 0 ? `테스트케이스 ${report.deletedTestcases}` : null,
          report.deletedTemplates > 0 ? `시작 코드 ${report.deletedTemplates}` : null,
          report.deletedNotifications > 0 ? `알림 ${report.deletedNotifications}` : null,
        ].filter(Boolean);
        // **상한에 걸렸다는 것을 반드시 말한다.** 안 그러면 한 번 눌렀으니 다 지워졌다고 믿는다.
        const rest = report.truncated ? " 상한에 걸려 남은 것이 있습니다 — 다시 누르거나 다음 자동 실행을 기다리십시오." : "";
        return `${parts.join(" · ")}개를 지웠습니다.${rest}`;
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
