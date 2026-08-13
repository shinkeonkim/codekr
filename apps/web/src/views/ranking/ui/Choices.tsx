"use client";

/** 랭킹 화면의 축 하나를 고르는 칩 줄. 기간·지표·소속이 같은 모양을 쓴다. */
export function Choices({
  options,
  value,
  onChange,
}: {
  options: { value: string; label: string }[];
  value: string;
  onChange: (next: string) => void;
}) {
  if (options.length === 0) return null;

  return (
    <div className="inline-flex rounded-lg border border-border p-0.5">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          aria-pressed={option.value === value}
          className={`rounded-md px-3 py-1 text-xs font-medium transition ${
            option.value === value ? "bg-brand text-brand-ink" : "text-ink-muted hover:text-ink"
          }`}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
