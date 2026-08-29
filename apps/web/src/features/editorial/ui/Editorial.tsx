"use client";

import { request } from "@/shared/api";
import { Card, CardTitle, CodeBlock, Markdown } from "@/shared/ui";
import { useEffect, useState } from "react";

interface EditorialState {
  body: string;
  referenceAnswer: string | null;
  referenceLabel: string | null;
}

/**
 * 모범 답안 (#719).
 *
 * **볼 수 없으면 자리 자체를 안 그린다.** 난이도 투표(#477)와 같은 규칙이다 — 서버가
 * 자격이 없을 때 404 로 "있다는 사실" 까지 감추기로 했는데, 화면이 "풀면 볼 수
 * 있습니다" 라고 말하면 그 판단을 되살리는 꼴이 된다.
 *
 * **기본으로 접어 둔다.** 서버가 자격을 이미 봤으니 펼쳐도 사고는 아니지만, 다시 풀어
 * 보려고 문제를 연 사람에게는 답이 먼저 보인다 (#139 가 질문의 코드 블록에 내린 판단과
 * 같다).
 */
export function Editorial({ slug }: { slug: string }) {
  const [state, setState] = useState<EditorialState | null>(null);

  useEffect(() => {
    let alive = true;
    request<EditorialState>(`/api/v1/problems/${slug}/editorial`, { auth: true })
      .then((next) => {
        if (alive) setState(next);
      })
      // 404(못 봄·없음)와 401(로그인 안 함)이 여기로 온다. 둘 다 안 그리는 것이 답이다.
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, [slug]);

  if (!state) return null;

  return (
    <Card>
      <details>
        <summary className="cursor-pointer list-none">
          <CardTitle>모범 답안 — 펼치면 풀이가 보입니다</CardTitle>
        </summary>
        <div className="mt-3 space-y-4">
          <Markdown source={state.body} />
          {state.referenceAnswer ? (
            <div className="space-y-1">
              <p className="text-xs text-ink-muted">
                {/* 무엇인지 말해 준다 — 유형마다 코드일 수도, git 명령일 수도, 쿼리일 수도 있다. */}
                참고 답안{state.referenceLabel ? ` · ${state.referenceLabel}` : ""}
              </p>
              <CodeBlock code={state.referenceAnswer} />
            </div>
          ) : null}
        </div>
      </details>
    </Card>
  );
}
