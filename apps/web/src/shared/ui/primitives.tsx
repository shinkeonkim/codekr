import { BrandCharacter } from "./brand/BrandCharacter";
import type { BrandCharacterName } from "./brand/BrandCharacter";
import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";

type Tone = "ok" | "danger" | "warn" | "info" | "muted";

const TONE_CLASSES: Record<Tone, string> = {
  ok: "bg-ok/12 text-ok border-ok/30",
  danger: "bg-danger/12 text-danger border-danger/30",
  warn: "bg-warn/12 text-warn border-warn/30",
  info: "bg-info/12 text-info border-info/30",
  muted: "bg-surface-muted text-ink-muted border-border",
};

export function Badge({ tone = "muted", children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium ${TONE_CLASSES[tone]}`}
    >
      {children}
    </span>
  );
}

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

export function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <label className="block space-y-1.5">
      <span className="text-sm font-medium text-ink">{label}</span>
      {children}
      {error ? <span className="block text-xs text-danger">{error}</span> : null}
    </label>
  );
}

const CONTROL_CLASS =
  "w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink outline-none placeholder:text-ink-muted focus:border-brand";

export function Input({ className = "", ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`${CONTROL_CLASS} ${className}`} />;
}

export function Textarea({ className = "", ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...props} className={`${CONTROL_CLASS} font-mono ${className}`} />;
}

export function Select({ className = "", ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={`${CONTROL_CLASS} ${className}`} />;
}

export function Alert({ tone = "danger", children }: { tone?: Tone; children: ReactNode }) {
  return (
    <div className={`rounded-lg border px-3 py-2 text-sm ${TONE_CLASSES[tone]}`} role="alert">
      {children}
    </div>
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
    <div className="rounded-card border border-dashed border-border px-6 py-12 text-center">
      {mascot ? (
        <BrandCharacter name={mascot} size={140} className="mx-auto mb-3 opacity-90" />
      ) : null}
      <p className="font-medium text-ink">{title}</p>
      {description ? <p className="mt-1 text-sm text-ink-muted">{description}</p> : null}
    </div>
  );
}
