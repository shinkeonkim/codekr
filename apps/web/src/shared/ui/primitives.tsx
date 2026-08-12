import { BrandCharacter } from "./brand/BrandCharacter";
import { Label } from "./label";
import type { BrandCharacterName } from "./brand/BrandCharacter";
import type { ButtonHTMLAttributes, ReactNode } from "react";

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-card border border-border bg-surface ${className}`}>{children}</div>
  );
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost" | "danger";
};

const BUTTON_VARIANTS: Record<NonNullable<ButtonProps["variant"]>, string> = {
  primary: "bg-brand text-brand-ink hover:opacity-90",
  secondary: "border border-border bg-surface text-ink hover:bg-surface-muted",
  ghost: "text-ink-muted hover:text-ink hover:bg-surface-muted",
  danger: "border border-danger/40 text-danger hover:bg-danger/10",
};

export function Button({ variant = "primary", className = "", ...props }: ButtonProps) {
  return (
    <button
      {...props}
      className={`inline-flex shrink-0 items-center justify-center gap-2 whitespace-nowrap rounded-lg px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50 ${BUTTON_VARIANTS[variant]} ${className}`}
    />
  );
}

/**
 * 이름표 + 오류를 한 묶음으로 두는 우리 것 (#291).
 *
 * shadcn 의 `Form` 은 `react-hook-form` 을 전제하므로 들이지 않았다. 이름표만
 * `Label` 로 갈아 끼운다 — 같은 것을 두 곳에 적지 않기 위해서다.
 */
export function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <label className="block space-y-1.5">
      <Label>{label}</Label>
      {children}
      {error ? <span className="block text-xs text-danger">{error}</span> : null}
    </label>
  );
}

export function EmptyState({
  title,
  description,
  /**
   * 캐릭터를 함께 보일지 (#261).
   *
   * **기본은 없음이다.** 모든 빈 화면에 넣으면 금방 지겨워지고, 오류를 알리는 자리에
   * 캐릭터가 있으면 상황을 가볍게 만든다. "아직 아무것도 없다" 를 말하는 자리에만 켠다.
   */
  mascot,
}: {
  title: string;
  description?: string;
  mascot?: BrandCharacterName;
}) {
  return (
    <div className="rounded-card border border-dashed border-border px-6 py-14 text-center">
      {mascot ? (
        <BrandCharacter name={mascot} size={220} className="mx-auto mb-4" />
      ) : null}
      <p className="font-medium text-ink">{title}</p>
      {description ? <p className="mt-1 text-sm text-ink-muted">{description}</p> : null}
    </div>
  );
}
