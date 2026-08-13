"use client";

import { affiliationApi } from "@/entities/affiliation";
import type { MyAffiliations } from "@/entities/affiliation";
import { ApiError } from "@/shared/api";
import { Badge, Button, Card, CardTitle, ConfirmDialog, useToast } from "@/shared/ui";
import { useEffect, useState } from "react";

/**
 * 내 소속 (#429, #398 화면).
 *
 * **"학교 인증" 이 아니라 "학교 메일 확인됨" 이다** (기획서 3절). 우리가 확인한 것은
 * "그 도메인의 메일을 받을 수 있다" 까지다 — 졸업생도 재학생으로 보인다. 화면이 그
 * 한계를 감추지 않게 **"인증" 이라는 말을 쓰지 않는다.**
 */
export function MyAffiliationsCard() {
  const toast = useToast();
  const [mine, setMine] = useState<MyAffiliations | null>(null);

  const reload = () =>
    affiliationApi
      .mine()
      .then(setMine)
      .catch(() => setMine({ attached: [], attachable: [] }));

  useEffect(() => {
    reload();
  }, []);

  const act = async (run: Promise<unknown>, done: string, failed: string) => {
    try {
      await run;
      toast.success(done);
      await reload();
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : failed);
    }
  };

  if (!mine) return null;

  return (
    <Card className="space-y-3 p-5">
      <div>
        <CardTitle>소속</CardTitle>
        <p className="mt-1 text-xs text-ink-muted">
          확인한 메일 주소의 도메인이 등록된 곳이면 붙일 수 있습니다. 붙인 소속은 프로필에
          보이고, 랭킹을 그 안에서 볼 수 있습니다.
        </p>
      </div>

      {mine.attached.map((each) => (
        <div key={each.affiliationId} className="flex items-center gap-2 text-sm">
          <Badge tone="muted">{each.kindLabel}</Badge>
          <span className="text-ink">{each.name}</span>
          {/* 어느 주소로 붙었는지는 **나만 본다.** 떼면 무엇이 사라지는지 알려면 필요하다. */}
          <span className="truncate text-xs text-ink-muted">{each.email}</span>
          <span className="flex-1" />
          <ConfirmDialog
            trigger={
              <Button variant="ghost" className="px-2 py-0.5 text-xs">
                떼기
              </Button>
            }
            title={`${each.name} 을 뗍니다`}
            description="프로필에서 사라지고 그 소속 랭킹에서도 빠집니다. 주소가 그대로면 다시 붙일 수 있습니다."
            confirmLabel="떼기"
            onConfirm={() =>
              act(affiliationApi.detach(each.affiliationId), "뗐습니다.", "떼지 못했습니다.")
            }
          />
        </div>
      ))}

      {mine.attachable.map((each) => (
        <div key={each.affiliationId} className="flex items-center gap-2 text-sm">
          <Badge tone="muted">{each.kindLabel}</Badge>
          <span className="text-ink">{each.name}</span>
          <span className="truncate text-xs text-ink-muted">{each.email}</span>
          <span className="flex-1" />
          <Button
            variant="secondary"
            className="px-2 py-0.5 text-xs"
            onClick={() =>
              act(affiliationApi.attach(each.affiliationId), "붙였습니다.", "붙이지 못했습니다.")
            }
          >
            붙이기
          </Button>
        </div>
      ))}

      {mine.attached.length === 0 && mine.attachable.length === 0 ? (
        // **막다른 문구를 두지 않는다.** 무엇을 해야 붙일 수 있는지 바로 위 카드가 답한다.
        <p className="text-xs text-ink-muted">
          붙일 수 있는 소속이 없습니다. 학교·회사 메일 주소를 위에서 먼저 확인해 주세요.
        </p>
      ) : null}
    </Card>
  );
}
