"use client";

import { adminContestApi } from "@/entities/contest";
import type { SharedAddress } from "@/entities/contest";
import { ApiError } from "@/shared/api";
import { Alert, Button, Card, CardTitle } from "@/shared/ui";
import { useState } from "react";

/**
 * 같은 주소에서 제출한 계정들 (#148, #545).
 *
 * **모으는 쪽만 있고 쓰는 쪽이 없었다.** #148 이 접수 시각·IP·User-Agent 를 남기는
 * 이유는 부정이 의심될 때 볼 근거를 만드는 것인데, 볼 방법이 없으면 목적은 못 이루면서
 * 보관 의무만 진다.
 *
 * ## 눌러야 보인다
 *
 * 화면을 열자마자 부르지 않는다. 이것은 **민감한 조회**라, 대회 설정을 고치러 들어온
 * 사람에게까지 참가자의 주소를 보여 줄 이유가 없다. 서버가 이미 "계정이 둘 이상 겹치는
 * 주소만" 으로 좁혀 주지만, 화면도 한 겹 더 둔다.
 */
export function ContestAuditPanel({ contestId }: { contestId: number }) {
  const [addresses, setAddresses] = useState<SharedAddress[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = async () => {
    setBusy(true);
    setError(null);
    try {
      setAddresses(await adminContestApi.sharedAddresses(contestId));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "감사 자료를 불러오지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="space-y-3 p-5">
      <CardTitle>제출 감사</CardTitle>
      <p className="text-xs leading-relaxed text-ink-muted">
        한 주소에서 두 계정 이상이 제출한 경우만 보여줍니다.{" "}
        <span className="text-ink">겹쳤다는 것이 곧 부정은 아닙니다</span> — 같은 집·학교·
        카페에서 풀면 주소가 겹칩니다. 사실만 보여주고 판단은 사람이 합니다.
      </p>

      <Button variant="secondary" disabled={busy} onClick={load}>
        {busy ? "불러오는 중…" : addresses === null ? "겹치는 주소 보기" : "다시 보기"}
      </Button>

      {error ? <Alert>{error}</Alert> : null}

      {addresses !== null ? (
        addresses.length === 0 ? (
          <p className="border-t border-border pt-3 text-xs text-ok">겹치는 주소가 없습니다.</p>
        ) : (
          <div className="space-y-2 border-t border-border pt-3">
            <p className="text-xs text-ink-muted">{addresses.length}개 주소에서 계정이 겹칩니다.</p>
            <ul className="space-y-1">
              {addresses.map((address) => (
                <li key={address.ip} className="flex flex-wrap items-baseline gap-2 text-sm">
                  <code className="text-xs text-ink-muted">{address.ip}</code>
                  <span className="text-xs text-danger">{address.accountCount}개 계정</span>
                  <span className="min-w-0 flex-1 break-words text-ink">{address.nicknames}</span>
                </li>
              ))}
            </ul>
          </div>
        )
      ) : null}
    </Card>
  );
}
