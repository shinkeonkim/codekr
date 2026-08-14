"use client";

import type { BadgeDryRun, BadgeRule } from "@/entities/badge";
import { Alert } from "@/shared/ui";

/** 규칙 편집기의 곁가지 둘 (#549). 편집기가 200줄을 넘어 떼어 냈다. */
export function RuleList({ rules, onPick }: { rules: BadgeRule[]; onPick: (rule: BadgeRule) => void }) {
  if (rules.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-1.5 border-t border-border pt-3">
      {rules.map((rule) => (
        <button
          key={rule.ruleKey}
          type="button"
          onClick={() => onPick(rule)}
          className="rounded-full border border-border px-2.5 py-1 text-xs text-ink-muted hover:border-ink-muted"
        >
          {rule.ruleKey}
        </button>
      ))}
    </div>
  );
}

export function DryRunResult({ result }: { result: BadgeDryRun | null }) {
  if (!result) return null;
  if (!result.valid) {
    // **틀린 자리를 짚어 준다** — "잘못된 규칙입니다" 로는 고칠 수 없다.
    return (
      <Alert tone="danger">
        <ul className="list-disc space-y-0.5 pl-4">
          {result.errors.map((error) => (
            <li key={error}>{error}</li>
          ))}
        </ul>
      </Alert>
    );
  }
  return (
    <Alert tone="ok">
      표본 {result.sampled}명 중 <strong>{result.matched}명</strong>이 받습니다.
      {result.matchesUser !== null
        ? ` 지정한 회원은 ${result.matchesUser ? "받습니다" : "받지 못합니다"}.`
        : ""}
    </Alert>
  );
}
