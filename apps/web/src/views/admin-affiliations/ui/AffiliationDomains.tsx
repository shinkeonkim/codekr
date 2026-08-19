"use client";

import { adminAffiliationApi } from "@/entities/affiliation";
import type { Affiliation } from "@/entities/affiliation";
import { Button, ConfirmDialog, Input, useToast } from "@/shared/ui";
import { useState } from "react";

/**
 * 한 소속의 도메인들 — 펼친 행 안에 산다 (#633).
 *
 * **접혀 있을 때는 입력칸이 없다.** 전에는 소속마다 카드가 하나였고 카드마다 입력칸이
 * 있어서, 소속이 열 개면 **아직 쓰지 않을 입력칸 열 개**가 화면을 채웠다.
 *
 * 잘못 넣으면 그 도메인을 가진 모두가 그 소속을 얻는다 — 붙이고 떼는 것을 한 번 묻고,
 * 무엇이 걸린 일인지 물음에 적는다 (#428).
 */
export function AffiliationDomains({
  affiliation,
  onChanged,
  onFail,
}: {
  affiliation: Affiliation;
  onChanged: () => Promise<unknown>;
  onFail: (caught: unknown, fallback: string) => void;
}) {
  const toast = useToast();
  const [domain, setDomain] = useState("");

  const addDomain = async () => {
    if (!domain.trim()) {
      toast.error("도메인을 입력해 주세요.");
      return;
    }
    try {
      await adminAffiliationApi.addDomain(affiliation.id, domain.trim());
      setDomain("");
      toast.success("붙였습니다.");
      await onChanged();
    } catch (caught) {
      // 한 도메인은 한 소속에만 붙는다 — 이미 다른 소속에 있으면 서버가 그렇게 말한다.
      onFail(caught, "붙이지 못했습니다.");
    }
  };

  const removeDomain = async (domainId: number) => {
    try {
      await adminAffiliationApi.removeDomain(affiliation.id, domainId);
      toast.success("뗐습니다.");
      await onChanged();
    } catch (caught) {
      onFail(caught, "떼지 못했습니다.");
    }
  };

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        {affiliation.domains.map((each) => (
          <span
            key={each.id}
            className="inline-flex items-center gap-1.5 rounded-md border border-border py-1 pl-2 pr-1 text-xs text-ink-muted"
          >
            @{each.domain}
            <ConfirmDialog
              trigger={
                // 글자 하나짜리 단추라 그냥 두면 **주소에 붙어 있고 누를 자리도 좁다.**
                <button
                  type="button"
                  className="rounded px-1 leading-none text-ink-muted transition hover:bg-surface-muted hover:text-ink"
                  aria-label={`${each.domain} 떼기`}
                >
                  ×
                </button>
              }
              title={`@${each.domain} 을 뗍니다`}
              description="이 도메인으로는 더 이상 이 소속을 붙일 수 없습니다. 이미 붙인 사람은 그대로 남습니다."
              confirmLabel="떼기"
              onConfirm={() => removeDomain(each.id)}
            />
          </span>
        ))}
      </div>

      <div className="flex gap-2">
        <Input
          placeholder="snu.ac.kr"
          value={domain}
          onChange={(event) => setDomain(event.target.value)}
        />
        <Button variant="secondary" onClick={addDomain}>
          도메인 붙이기
        </Button>
      </div>
    </div>
  );
}
