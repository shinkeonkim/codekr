"use client";

import type { ProblemSummary } from "@/entities/problem";
import { Button, ConfirmDialog } from "@/shared/ui";
import Link from "next/link";

/**
 * 한 문제에 걸 수 있는 것 (#625).
 *
 * **행에서 떼어 둔 것은 열이 늘기 때문이다.** 회원 관리(`AdminUserActions`)가 같은
 * 이유로 떨어져 있고, #627 이 여기에 일괄 선택을 얹으면 목록 파일이 더 커진다.
 */
export function AdminProblemActions({
  problem,
  onRemove,
}: {
  problem: ProblemSummary;
  onRemove: (id: number) => void;
}) {
  return (
    <span className="flex justify-end gap-2">
      <Button asChild variant="secondary" className="px-3 py-1 text-xs">
        <Link href={`/admin/problems/${problem.id}/edit`}>수정</Link>
      </Button>
      {/* 되묻는 것은 이 버튼이 한다 (#291 4단계). */}
      <ConfirmDialog
        title={`'${problem.title}' 문제를 삭제할까요?`}
        description="제출 이력은 그대로 남습니다. 문제만 목록에서 사라집니다."
        confirmLabel="삭제"
        onConfirm={() => onRemove(problem.id)}
        trigger={
          <Button variant="danger" className="px-3 py-1 text-xs">
            삭제
          </Button>
        }
      />
    </span>
  );
}
