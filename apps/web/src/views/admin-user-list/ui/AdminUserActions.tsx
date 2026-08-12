"use client";

import { userApi } from "@/entities/user";
import type { AdminUserSummary } from "@/entities/user";
import { useAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { Button, Input, Select } from "@/shared/ui";
import { useState } from "react";

const ASSIGNABLE = ["USER", "PROBLEM_SETTER", "CONTEST_MANAGER", "BOARD_MANAGER", "ADMIN", "SUPERUSER"];

/**
 * 한 회원에게 할 수 있는 일 (#223).
 *
 * **최고 관리자에게만 보인다.** 서버가 막으므로 눌러도 안 되지만, 눌렀을 때 403 이
 * 나는 버튼은 고장으로 보인다 (#131 의 내비가 같은 이유로 감춘다).
 */
export function AdminUserActions({
  target,
  self,
  onDone,
  onError,
}: {
  target: AdminUserSummary;
  self: boolean;
  onDone: (message: string) => void;
  onError: (message: string) => void;
}) {
  const { isSuperuser } = useRoles();
  const [open, setOpen] = useState(false);
  const [roles, setRoles] = useState<string[]>(target.roles);
  const [confirmation, setConfirmation] = useState("");
  const [running, setRunning] = useState(false);

  if (!isSuperuser || target.withdrawnAt) return null;

  const run = async (action: () => Promise<unknown>, message: string) => {
    setRunning(true);
    try {
      await action();
      setOpen(false);
      setConfirmation("");
      onDone(message);
    } catch (caught) {
      onError(caught instanceof ApiError ? caught.message : "작업에 실패했습니다.");
    } finally {
      setRunning(false);
    }
  };

  if (!open) {
    return (
      <Button variant="ghost" onClick={() => setOpen(true)}>
        관리
      </Button>
    );
  }

  return (
    <div className="space-y-2 rounded-lg border border-border p-3 text-left">
      <Select
        aria-label="역할"
        value={roles[0] ?? "USER"}
        onChange={(event) => setRoles([event.target.value])}
      >
        {ASSIGNABLE.map((role) => (
          <option key={role} value={role}>
            {role}
          </option>
        ))}
      </Select>
      <Button
        onClick={() => run(() => userApi.replaceRoles(target.id, roles), `${target.nickname} 의 역할을 바꿨습니다.`)}
        disabled={running}
      >
        역할 저장
      </Button>

      {/*
        **강제 탈퇴는 되돌릴 수 없다** (#140). 이메일·닉네임이 그 자리에서 지워진다 —
        유예 기간이 없다. 목록의 버튼 하나로 그 일이 일어나면 안 되므로 닉네임을 그대로
        옮겨 적게 한다.

        자기 자신에게는 아예 보이지 않는다.
      */}
      {self ? (
        <p className="text-xs text-ink-muted">자기 계정은 여기서 탈퇴시킬 수 없습니다.</p>
      ) : (
        <>
          <Input
            placeholder={`강제 탈퇴하려면 "${target.nickname}" 입력`}
            value={confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
          />
          <Button
            variant="danger"
            disabled={running || confirmation !== target.nickname}
            onClick={() =>
              run(() => userApi.forceWithdraw(target.id), `${target.nickname} 을(를) 탈퇴 처리했습니다.`)
            }
          >
            강제 탈퇴
          </Button>
        </>
      )}

      <Button variant="ghost" onClick={() => setOpen(false)}>
        닫기
      </Button>
    </div>
  );
}

/** 역할 위계는 화면도 안다 — 감출지 말지를 정하기 위해서다 (#131). */
function useRoles() {
  const { user } = useAuth();
  return { isSuperuser: user?.roles?.includes("SUPERUSER") ?? false };
}
