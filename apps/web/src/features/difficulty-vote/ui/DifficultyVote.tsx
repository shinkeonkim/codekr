"use client";

import { ALL_DIFFICULTIES, difficultyLabel } from "@/entities/problem";
import { request } from "@/shared/api";
import { Button, Card, CardTitle, Select } from "@/shared/ui";
import { useEffect, useState } from "react";

interface VoteState {
  myLevel: number | null;
  voteCount: number;
  medianLevel: number | null;
  canVote: boolean;
}

/**
 * 난이도 투표 (#477).
 *
 * **푼 사람만 매길 수 있다.** 못 푼 사람에게는 자리 자체를 그리지 않는다 — 눌러 보고
 * 거절당하는 것보다 처음부터 없는 편이 낫다.
 *
 * **분포는 내가 투표한 뒤에 보인다.** 먼저 보면 끌려가고, 그러면 모인 숫자는 문제의
 * 난이도가 아니라 처음 몇 표의 메아리가 된다.
 */
export function DifficultyVote({ slug }: { slug: string }) {
  const [state, setState] = useState<VoteState | null>(null);
  const [level, setLevel] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let alive = true;
    request<VoteState>(`/api/v1/problems/${slug}/difficulty-vote`, {
      auth: true,
    })
      .then((next) => {
        if (!alive) return;
        setState(next);
        if (next.myLevel !== null) setLevel(String(next.myLevel));
      })
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, [slug]);

  if (!state?.canVote) return null;

  const submit = async () => {
    if (!level || busy) return;
    setBusy(true);
    try {
      setState(
        await request<VoteState>(`/api/v1/problems/${slug}/difficulty-vote`, {
          method: "POST",
          body: { level: Number(level) },
          auth: true,
        }),
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="space-y-3 p-5">
      <CardTitle>이 문제의 난이도는</CardTitle>
      <p className="text-xs text-ink-muted">
        푼 사람만 매길 수 있습니다. 표는 언제든 바꿀 수 있습니다.
      </p>
      <div className="flex items-end gap-2">
        <Select
          aria-label="난이도"
          className="w-56"
          value={level}
          onChange={(event) => setLevel(event.target.value)}
        >
          <option value="">고르세요</option>
          {ALL_DIFFICULTIES.map((difficulty, index) => (
            <option key={difficulty} value={index + 1}>
              {difficultyLabel(difficulty)}
            </option>
          ))}
        </Select>
        <Button onClick={submit} disabled={!level || busy}>
          {state.myLevel === null ? "투표" : "바꾸기"}
        </Button>
      </div>
      {state.myLevel === null ? (
        // 투표하기 전에는 결과를 감춘다 — 그것이 이 기능의 전부다.
        <p className="text-xs text-ink-muted">
          {state.voteCount}명이 매겼습니다. 투표하면 결과가 보입니다.
        </p>
      ) : (
        <p className="text-sm text-ink">
          {state.voteCount}명의 가운데값:{" "}
          <b>
            {state.medianLevel === null
              ? "아직 없음"
              : difficultyLabel(ALL_DIFFICULTIES[state.medianLevel - 1])}
          </b>
        </p>
      )}
    </Card>
  );
}
