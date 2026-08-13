import { BrandCharacter } from "./brand/BrandCharacter";
import type { BrandCharacterName } from "./brand/BrandCharacter";
import type { ReactNode } from "react";

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-card border border-border bg-surface ${className}`}>{children}</div>
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
