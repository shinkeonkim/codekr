"use client";

import { userApi } from "@/entities/user";
import type { AdminUserSummary } from "@/entities/user";
import { EmailVerificationActions } from "@/features/admin-email-verification";
import { ActiveSuspensions, SuspendForm } from "@/features/admin-suspension";
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
  const { isAdmin, isSuperuser } = useRoles();
  // 정지·해제 뒤에 걸려 있는 목록을 다시 읽는 방아쇠.
  const [suspensionKey, setSuspensionKey] = useState(0);
  const [open, setOpen] = useState(false);
  const [roles, setRoles] = useState<string[]>(target.roles);
  const [confirmation, setConfirmation] = useState("");
  const [reason, setReason] = useState("");
  const [running, setRunning] = useState(false);

  // **정지는 ADMIN 도 건다** (#224). 되돌릴 수 있는 조치라, 실제로 게시판을
  // 지키는 사람이 쓸 수 있어야 한다. 역할 변경·강제 탈퇴만 SUPERUSER 다.
  if (!isAdmin || target.withdrawnAt) return null;

  const run = async (action: () => Promise<unknown>, message: string) => {
    setRunning(true);
    try {
      await action();
      setOpen(false);
      setConfirmation("");
      setReason("");
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
      <ActiveSuspensions
        userId={target.id}
        reloadKey={suspensionKey}
        onDone={(message) => {
          setSuspensionKey((key) => key + 1);
          onDone(message);
        }}
        onError={onError}
      />
      {/*
        사유 칸은 **되돌릴 수 없거나 남에게 보이는 조치가 함께 쓴다** (#225).
        소개 지우기와 강제 탈퇴 둘 다 서버가 사유를 요구하므로 한 칸으로 둔다 —
        조치마다 칸을 두면 어느 것을 채워야 하는지가 화면마다 달라진다.
      */}
      <Input
        placeholder="사유 (지우기·탈퇴에 필요)"
        value={reason}
        onChange={(event) => setReason(event.target.value)}
      />

      {/*
        소개 지우기 (#310). **신고 기능이 없어도 지울 길은 있어야 한다** — 프로필은
        남에게 보이는 자리라 광고나 사칭이 적힐 수 있다. 고쳐 쓰지 않고 지우기만 한다.
      */}
      <Button
        variant="ghost"
        disabled={running || reason.trim().length === 0}
        onClick={() =>
          run(() => userApi.clearBio(target.id, reason.trim()), `${target.nickname} 의 소개를 지웠습니다.`)
        }
      >
        소개 지우기
      </Button>

      {/* 인증이 막혀 글을 못 쓰는 사람을 여기서 풀어 준다 (#524). */}
      <EmailVerificationActions
        userId={target.id}
        verifiedAt={target.emailVerifiedAt}
        canForce={isSuperuser}
        reason={reason}
        onDone={onDone}
        onError={onError}
      />

      <SuspendForm
        userId={target.id}
        onDone={(message) => {
          setSuspensionKey((key) => key + 1);
          onDone(message);
        }}
        onError={onError}
      />

      {!isSuperuser ? null : (
      <>
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
          {/* 사유는 위의 칸을 쓴다 — 서버가 요구한다 (#225). */}
          <Button
            variant="danger"
            disabled={running || confirmation !== target.nickname || reason.trim().length === 0}
            onClick={() =>
              run(
                () => userApi.forceWithdraw(target.id, reason.trim()),
                `${target.nickname} 을(를) 탈퇴 처리했습니다.`,
              )
            }
          >
            강제 탈퇴
          </Button>
        </>
      )}

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
  const roles = user?.roles ?? [];
  const isSuperuser = roles.includes("SUPERUSER");
  return { isSuperuser, isAdmin: isSuperuser || roles.includes("ADMIN") };
}
