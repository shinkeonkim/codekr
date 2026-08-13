"use client";

import { affiliationApi } from "@/entities/affiliation";
import type { AttachedAffiliation } from "@/entities/affiliation";
import { useAuth } from "@/features/auth";
import { useEffect, useState } from "react";
import { Choices } from "./Choices";

const ALL = "";

/**
 * 내 소속으로 랭킹을 좁힌다 (#399).
 *
 * **내가 붙인 소속만 고를 수 있다.** 소속 목록은 어드민만 보고(#397), 여기서 남의
 * 소속을 뒤지게 하면 "어느 학교에 누가 있나" 를 묻는 화면이 되어 버린다. 이 기능이
 * 있는 이유는 **내가 있는 곳 안에서의 순위**다.
 */
export function AffiliationFilter({
  value,
  onChange,
}: {
  value: number | undefined;
  onChange: (next: number | undefined) => void;
}) {
  const { user } = useAuth();
  const [attached, setAttached] = useState<AttachedAffiliation[]>([]);

  useEffect(() => {
    // 랭킹은 공개 화면이다. 로그인 안 한 사람에게 401 을 받으러 가지 않는다.
    if (!user) return;
    affiliationApi
      .mine()
      .then((mine) => setAttached(mine.attached))
      // 소속은 랭킹의 곁가지다. 못 불러오면 조용히 전체 랭킹만 보여 준다.
      .catch(() => setAttached([]));
  }, [user]);

  // 붙은 소속이 없으면 고를 것이 없다 — 빈 칩 줄을 두지 않는다.
  // 로그아웃하면 `user` 가 없어지므로 남은 목록도 함께 사라진다.
  if (!user || attached.length === 0) return null;

  return (
    <Choices
      options={[
        { value: ALL, label: "전체" },
        ...attached.map((it) => ({ value: String(it.affiliationId), label: it.name })),
      ]}
      value={value === undefined ? ALL : String(value)}
      onChange={(next) => onChange(next === ALL ? undefined : Number(next))}
    />
  );
}
