"use client";

import type { ContestUpsert } from "@/entities/contest";
import { Button, Field } from "@/shared/ui";

/**
 * 대회의 정책 두 가지 — 누가 보는가(#465), 누가 참가하는가(#466).
 *
 * 폼에서 떼어 둔 이유: 나머지 칸은 값을 받는 것뿐인데 **이 둘은 켰을 때 무슨 일이
 * 생기는지를 함께 말해야** 해서, 칸 하나에 설명이 딸린다.
 */
export function ContestPolicyFields({
  values,
  update,
}: {
  values: ContestUpsert;
  update: <K extends keyof ContestUpsert>(key: K, value: ContestUpsert[K]) => void;
}) {
  return (
    <>
        {/*
          **`status` 와 다른 값이다** (#465). 그쪽은 "준비 중인가", 이쪽은 "누가 보는가" 다.
          목록에 없는 대회도 주소를 알면 들어온다 — 비밀이 아니라는 것을 화면이 말한다.
        */}
        <Field label="공개 범위">
          <div className="flex gap-2">
            {(["PUBLIC", "UNLISTED"] as const).map((each) => (
              <Button
                key={each}
                variant={values.visibility === each ? "primary" : "secondary"}
                className="px-3 py-1 text-xs"
                onClick={() => update("visibility", each)}
              >
                {each === "PUBLIC" ? "누구나 보기" : "링크가 있는 사람만"}
              </Button>
            ))}
          </div>
        </Field>
        {values.visibility === "UNLISTED" ? (
          <p className="text-xs text-ink-muted">
            목록과 검색에는 나오지 않습니다. <span className="text-ink">비밀은 아닙니다</span> —
            주소를 아는 사람은 들어옵니다. 문제와 순위표는 시작 시각·참가 여부가 막습니다.
          </p>
        ) : null}
        {/*
          승인을 켜는 자리 (#466). **화면에 없어서 켤 수조차 없었다** (#543).
          켜면 신청자 목록이 대회 상세에 뜬다.
        */}
        <Field label="참가 승인">
          <div className="flex gap-2">
            {([false, true] as const).map((each) => (
              <Button
                key={String(each)}
                variant={values.requiresApproval === each ? "primary" : "secondary"}
                className="px-3 py-1 text-xs"
                onClick={() => update("requiresApproval", each)}
              >
                {each ? "승인해야 참가" : "신청하면 바로 참가"}
              </Button>
            ))}
          </div>
        </Field>
        {values.requiresApproval ? (
          <p className="text-xs text-ink-muted">
            신청한 사람은 <span className="text-ink">승인할 때까지 참가자가 아닙니다</span> —
            문제도 못 보고 제출도 못 합니다. 대회 상세에서 신청자를 승인하십시오.
          </p>
        ) : null}    </>
  );
}
