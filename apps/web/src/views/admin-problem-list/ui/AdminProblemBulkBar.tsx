"use client";

import { Button, ConfirmDialog } from "@/shared/ui";

/**
 * 고른 것에 한 번에 거는 줄 (#627).
 *
 * **고른 것이 없으면 나타나지 않는다.** 늘 떠 있으면 누를 수 없는 버튼 둘이 목록 위
 * 자리를 먹고, 무엇이 골라졌는지도 눈에 덜 띈다.
 *
 * 공개는 되돌릴 수 있어 그냥 누르고, **비공개는 되묻는다** — 사람들이 풀고 있던 문제가
 * 목록에서 사라지는 일이고, 대회·문제집에 걸려 있으면 그쪽에서도 사라진다.
 */
export function AdminProblemBulkBar({
  count,
  onPublish,
  onClear,
}: {
  count: number;
  onPublish: (published: boolean) => void;
  onClear: () => void;
}) {
  if (count === 0) return null;

  return (
    <div className="flex flex-wrap items-center gap-2 rounded-card border border-border bg-surface-muted/40 px-4 py-2.5">
      <span className="text-sm text-ink">
        <strong className="font-semibold">{count}개</strong> 선택됨
      </span>
      <span className="ml-auto flex flex-wrap gap-2">
        <Button className="px-3 py-1 text-xs" onClick={() => onPublish(true)}>
          공개하기
        </Button>
        <ConfirmDialog
          title={`${count}개를 비공개로 돌릴까요?`}
          description="풀던 사람에게는 문제가 사라진 것으로 보입니다. 제출 이력은 남습니다."
          confirmLabel="비공개"
          onConfirm={() => onPublish(false)}
          trigger={
            <Button variant="secondary" className="px-3 py-1 text-xs">
              비공개로
            </Button>
          }
        />
        <Button variant="ghost" className="px-3 py-1 text-xs" onClick={onClear}>
          선택 해제
        </Button>
      </span>
    </div>
  );
}
