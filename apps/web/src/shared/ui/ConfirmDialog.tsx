"use client";

import { Button } from "@/shared/ui/button";
import * as AlertDialog from "@radix-ui/react-alert-dialog";
import type { ReactNode } from "react";
import { useState } from "react";

/**
 * 되돌릴 수 없는 일을 하기 전에 한 번 묻는다 (#291 4단계).
 *
 * **`confirm()` 을 걷는다.** 브라우저 기본 대화상자는 세 가지를 못 한다.
 *
 * 1. **모양을 손댈 수 없다.** 브라우저·OS 마다 다르게 뜨고, 무엇이 지워지는지
 *    굵게 보여 줄 수도 없다
 * 2. **자바스크립트를 멈춰 세운다.** 그동안 화면은 아무것도 못 한다 —
 *    그리고 자동화된 확인이 아예 불가능해진다
 * 3. **어느 창에 뜨는지 사용자가 고르지 못한다.** 탭 위에 얹히는 것이라
 *    무엇에 대한 물음인지 맥락이 끊긴다
 *
 * `AlertDialog` 를 쓰는 이유는 `Dialog` 와 다르기 때문이다 — **바깥을 눌러서 닫히지
 * 않고**, 스크린 리더에 `alertdialog` 로 알려지며, 확인·취소가 반드시 있어야 한다.
 * 삭제를 묻는 자리에 맞는 것은 그쪽이다.
 */
export function ConfirmDialog({
  trigger,
  title,
  description,
  /** 확인 버튼 문구. **"확인" 이 아니라 무엇을 하는지 적는다** — "삭제" 처럼. */
  confirmLabel = "확인",
  /** 되돌릴 수 없는 일이면 `danger`. 확인 버튼의 색이 그것을 말한다. */
  tone = "danger",
  onConfirm,
}: {
  trigger: ReactNode;
  title: string;
  description?: ReactNode;
  confirmLabel?: string;
  tone?: "danger" | "primary";
  onConfirm: () => void | Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [working, setWorking] = useState(false);

  const confirm = async () => {
    // **누르는 동안 닫지 않는다.** 서버가 거절하면 그 사실을 이 자리에서 알아야 하고,
    // 먼저 닫아 버리면 사용자는 성공한 줄 안다.
    setWorking(true);
    try {
      await onConfirm();
      setOpen(false);
    } finally {
      setWorking(false);
    }
  };

  return (
    <AlertDialog.Root open={open} onOpenChange={setOpen}>
      <AlertDialog.Trigger asChild>{trigger}</AlertDialog.Trigger>
      <AlertDialog.Portal>
        {/* `z-modal` 은 헤더(`z-header`)보다 위다. 아래 화면이 눌리면 안 된다. */}
        <AlertDialog.Overlay className="fixed inset-0 z-modal bg-black/40" />
        <AlertDialog.Content className="fixed left-1/2 top-1/2 z-modal w-[min(28rem,calc(100vw-2rem))] -translate-x-1/2 -translate-y-1/2 rounded-card border border-border bg-surface p-5 shadow-lg">
          <AlertDialog.Title className="text-sm font-semibold text-ink">{title}</AlertDialog.Title>
          {description ? (
            <AlertDialog.Description className="mt-2 text-sm text-ink-muted">
              {description}
            </AlertDialog.Description>
          ) : null}
          <div className="mt-5 flex justify-end gap-2">
            {/*
              **취소가 먼저다.** 되돌릴 수 없는 일에서 손이 먼저 가는 자리에 위험한
              버튼을 두지 않는다. 그리고 `AlertDialog` 는 Esc 로도 취소된다.
            */}
            <AlertDialog.Cancel asChild>
              <Button variant="secondary" disabled={working}>
                취소
              </Button>
            </AlertDialog.Cancel>
            <Button
              variant={tone === "danger" ? "danger" : "primary"}
              disabled={working}
              onClick={confirm}
            >
              {working ? "처리 중…" : confirmLabel}
            </Button>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  );
}
