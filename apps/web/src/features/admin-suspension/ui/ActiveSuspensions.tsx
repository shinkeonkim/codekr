"use client";

import { userApi } from "@/entities/user";
import type { Suspension } from "@/entities/user";
import { ApiError } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Badge, Button } from "@/shared/ui";
import { useEffect, useState } from "react";

/**
 * 지금 걸려 있는 정지와 해제 (#224).
 *
 * **기한이 지난 것은 여기 오지 않는다** — 서버가 조회에서 빼므로 저절로 풀린 것을
 * 사람이 정리할 일이 없다. 여기 있는 것은 아직 효력이 있는 것뿐이다.
 */
export function ActiveSuspensions({
  userId,
  reloadKey,
  onDone,
  onError,
}: {
  userId: number;
  reloadKey: number;
  onDone: (message: string) => void;
  onError: (message: string) => void;
}) {
  const [items, setItems] = useState<Suspension[]>([]);

  useEffect(() => {
    let cancelled = false;
    userApi
      .activeSuspensions(userId)
      .then((data) => {
        if (!cancelled) setItems(data);
      })
      .catch(() => {
        if (!cancelled) setItems([]);
      });
    return () => {
      cancelled = true;
    };
  }, [userId, reloadKey]);

  if (items.length === 0) return null;

  const lift = async (suspension: Suspension) => {
    try {
      await userApi.liftSuspension(userId, suspension.id);
      onDone(`${suspension.scopeLabel} 제한을 풀었습니다.`);
    } catch (caught) {
      onError(caught instanceof ApiError ? caught.message : "해제에 실패했습니다.");
    }
  };

  return (
    <ul className="space-y-1">
      {items.map((each) => (
        <li key={each.id} className="flex flex-wrap items-center gap-2 text-xs">
          <Badge tone="danger">{each.scopeLabel} 제한</Badge>
          <span className="text-ink-muted">
            {each.endsAt ? `${formatDateTime(each.endsAt)} 까지` : "기한 없음"} · {each.reason}
          </span>
          <Button variant="ghost" onClick={() => lift(each)}>
            해제
          </Button>
        </li>
      ))}
    </ul>
  );
}
