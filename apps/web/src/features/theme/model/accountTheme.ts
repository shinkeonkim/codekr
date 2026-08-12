"use client";

import { userApi } from "@/entities/user";
import { setTheme } from "@/shared/theme";
import type { Theme } from "@/shared/theme";

/**
 * 계정에 저장된 테마와 이 기기의 선택을 잇는다 (#274).
 *
 * ## 어느 쪽이 이기는가
 *
 * **로그인한 순간에는 계정 값이 이긴다.** 그 뒤 이 기기에서 고치면 양쪽이 함께 바뀐다.
 *
 * 그 반대(기기가 이긴다)도 말은 되지만, 그러면 계정에 저장하는 뜻이 절반 사라진다 —
 * 새 기기에서 열 때마다 그 기기의 기본값이 계정 값을 덮어쓴다.
 *
 * ## 서버 값을 기기에도 남긴다
 *
 * 남기지 않으면 **로그인할 때마다 화면이 한 번 뒤집힌다.** 인라인 스크립트(#206)가
 * 첫 그림 전에 읽는 것은 기기의 값뿐이라, 계정 값은 인증이 끝난 뒤에야 온다.
 * 한 번 받아 기기에 적어 두면 다음 방문부터는 뒤집힘이 없다 — **새 기기의 첫 방문
 * 한 번**만 남는다.
 */
export function applyAccountTheme(accountTheme: Theme | null): void {
  // 고른 적이 없으면 이 기기의 선택을 그대로 둔다. 서버가 모른다고 덮어쓰면 안 된다.
  if (!accountTheme) return;
  setTheme(accountTheme);
}

/** 이 기기에서 고른 값을 계정에도 올린다. 실패해도 이 기기에서는 이미 바뀌어 있다. */
export async function saveAccountTheme(theme: Theme): Promise<void> {
  try {
    await userApi.updateSettings({ theme: toServer(theme) });
  } catch {
    // 계정 저장에 실패해도 화면은 이미 바뀌었다. 다음에 고칠 때 다시 시도된다 —
    // 여기서 되돌리면 방금 고른 것이 눈앞에서 사라진다.
  }
}

function toServer(theme: Theme): "LIGHT" | "DARK" | "SYSTEM" {
  if (theme === "light") return "LIGHT";
  if (theme === "dark") return "DARK";
  return "SYSTEM";
}

/** 서버 값을 이 저장소의 표기로. */
export function fromServer(theme: "LIGHT" | "DARK" | "SYSTEM" | null): Theme | null {
  if (theme === "LIGHT") return "light";
  if (theme === "DARK") return "dark";
  if (theme === "SYSTEM") return "system";
  return null;
}
