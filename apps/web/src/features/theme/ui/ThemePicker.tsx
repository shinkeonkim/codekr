"use client";

import { useAuth } from "@/features/auth";
import { THEME_LABELS, useTheme } from "@/shared/theme";
import { saveAccountTheme } from "../model/accountTheme";
import type { Theme } from "@/shared/theme";

const OPTIONS: Theme[] = ["system", "light", "dark"];

/**
 * 테마 고르기 (#206).
 *
 * **버튼 셋을 나란히 둔다.** 켜고 끄는 스위치로 만들면 "시스템 따름" 을 표현할 자리가
 * 없다 — 지금 무엇이 골라져 있는지도 스위치의 위치로만 알게 된다.
 *
 * 저장 버튼이 없다. 테마는 누르는 순간 눈으로 결과가 보이므로, 저장을 한 번 더
 * 누르게 하면 **이미 일어난 일을 확인시키는** 절차가 된다.
 *
 * 로그인했으면 계정에도 올린다 (#274). 올리는 데 실패해도 이 기기에서는 이미 바뀌어
 * 있다 — 되돌리면 방금 고른 것이 눈앞에서 사라진다.
 */
export function ThemePicker() {
  const { theme, setTheme } = useTheme();
  const { user } = useAuth();

  return (
    <div>
      <div className="flex gap-2" role="radiogroup" aria-label="화면 테마">
        {OPTIONS.map((option) => {
          const selected = theme === option;
          return (
            <button
              key={option}
              type="button"
              role="radio"
              aria-checked={selected}
              onClick={() => {
                setTheme(option);
                // 로그인했으면 계정에도 올린다 (#274) — 기기를 옮겨도 따라오게.
                if (user) void saveAccountTheme(option);
              }}
              className={`flex-1 rounded-card border px-3 py-2 text-sm transition ${
                selected
                  ? "border-brand bg-brand text-brand-ink"
                  : "border-border text-ink hover:border-brand"
              }`}
            >
              {THEME_LABELS[option].label}
            </button>
          );
        })}
      </div>
      <p className="mt-2 text-xs text-ink-muted">{THEME_LABELS[theme].hint}</p>
    </div>
  );
}
