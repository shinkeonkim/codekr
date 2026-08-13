"use client";

import { userApi } from "@/entities/user";
import type { TermSummary } from "@/entities/user";
import { CheckboxField } from "@/shared/ui";
import Link from "next/link";
import { useEffect, useState } from "react";

/**
 * 가입할 때 받는 동의 (#235).
 *
 * **한 번에 체크하는 버튼이 있어도 개별 상태가 남아야 한다** — "전체 동의" 만 기록하면
 * 나중에 무엇에 동의했는지 말할 수 없다. 여기서는 늘 개별 id 목록을 올린다.
 */
export function TermsAgreement({
  onChange,
  onRequiredChange,
}: {
  onChange: (documentIds: number[]) => void;
  /** 필수 판의 id — 부르는 쪽이 "다 받았는지" 를 판단한다. */
  onRequiredChange?: (documentIds: number[]) => void;
}) {
  const [terms, setTerms] = useState<TermSummary[]>([]);
  const [agreed, setAgreed] = useState<number[]>([]);

  useEffect(() => {
    userApi
      .terms()
      .then((found) => {
        setTerms(found);
        onRequiredChange?.(found.filter((term) => term.required).map((term) => term.id));
      })
      .catch(() => setTerms([]));
  }, [onRequiredChange]);

  const apply = (next: number[]) => {
    setAgreed(next);
    onChange(next);
  };

  if (terms.length === 0) return null;

  const allAgreed = terms.every((term) => agreed.includes(term.id));

  return (
    <div className="space-y-2 rounded-lg border border-border p-3">
      <CheckboxField
        label={<span className="font-medium text-ink">약관 전체 동의</span>}
        checked={allAgreed}
        onCheckedChange={(next) => apply(next ? terms.map((term) => term.id) : [])}
      />
      <div className="space-y-1.5 border-t border-border pt-2">
        {terms.map((term) => (
          <CheckboxField
            key={term.id}
            checked={agreed.includes(term.id)}
            onCheckedChange={(next) =>
              apply(next ? [...agreed, term.id] : agreed.filter((id) => id !== term.id))
            }
            label={
              <span className="text-xs">
                {term.required ? "(필수) " : "(선택) "}
                {/* **전문을 읽을 수 있어야 동의다.** 새 창으로 열어 쓰던 것을 잃지 않게 한다. */}
                <Link
                  href={`/terms/${term.id}`}
                  target="_blank"
                  className="text-brand hover:underline"
                >
                  {term.title} ({term.version}) 보기
                </Link>
              </span>
            }
          />
        ))}
      </div>
    </div>
  );
}
