"use client";

import { adminAffiliationApi } from "@/entities/affiliation";
import type { Affiliation } from "@/entities/affiliation";
import { Badge, Button, Card, ConfirmDialog, Input, useToast } from "@/shared/ui";
import { useState } from "react";

/**
 * 소속 하나와 그 도메인들 (#428, #397 화면).
 *
 * **잘못 넣으면 그 도메인을 가진 모두가 그 소속을 얻는다.** 그래서 붙이고 떼는 것을
 * 한 번 묻고, 무엇이 걸린 일인지 물음에 적는다.
 */
export function AffiliationCard({
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

  const remove = async () => {
    try {
      await adminAffiliationApi.remove(affiliation.id);
      toast.success("내렸습니다.");
      await onChanged();
    } catch (caught) {
      onFail(caught, "내리지 못했습니다.");
    }
  };

  return (
    <Card className="space-y-3">
      <div className="flex items-center gap-2">
        <span className="font-semibold text-ink">{affiliation.name}</span>
        <Badge tone="muted">{affiliation.kindLabel}</Badge>
        <span className="flex-1" />
        <ConfirmDialog
          trigger={
            <Button variant="ghost" className="px-2 py-0.5 text-xs">
              내리기
            </Button>
          }
          title={`'${affiliation.name}' 을 내립니다`}
          description="도메인이 함께 떨어져 새로 붙는 사람이 없어집니다. 이미 붙인 사람에게는 그대로 남습니다."
          confirmLabel="내리기"
          onConfirm={remove}
        />
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {affiliation.domains.length === 0 ? (
          // 도메인이 없으면 이 소속은 있으나 마나다. 그것을 눈에 띄게 말한다.
          <span className="text-xs text-danger">도메인이 없어 아무에게도 붙지 않습니다.</span>
        ) : null}
        {affiliation.domains.map((each) => (
          <span
            key={each.id}
            className="inline-flex items-center gap-1 rounded-md border border-border px-2 py-0.5 text-xs text-ink-muted"
          >
            @{each.domain}
            <ConfirmDialog
              trigger={
                <button type="button" className="text-ink-muted hover:text-ink" aria-label={`${each.domain} 떼기`}>
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
    </Card>
  );
}
