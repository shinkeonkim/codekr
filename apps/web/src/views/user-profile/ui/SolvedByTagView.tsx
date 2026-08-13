import type { SolvedByTag } from "@/entities/user";
import { Card, CardTitle } from "@/shared/ui";

/**
 * 태그별 푼 문제 수 (#232).
 *
 * 난이도 분포가 "얼마나 어려운 것을 푸는가" 라면, 이것은 **무엇을 풀어 봤고 무엇을
 * 안 풀어 봤는가**다. 코딩 테스트 준비에서 다음에 무엇을 할지 정하는 근거가 된다.
 *
 * 합이 푼 문제 수와 다를 수 있다 — 태그가 없는 문제는 어디에도 세지 않고, 태그가 둘인
 * 문제는 두 곳에 센다. 숨기지 않고 그대로 둔다. 억지로 맞추면 "기타" 같은 칸이 생기는데,
 * 그것은 분류가 아니라 분류하지 않았다는 뜻이라 읽는 사람을 헷갈리게 한다.
 */
export function SolvedByTagView({ solvedByTag }: { solvedByTag: SolvedByTag[] }) {
  if (solvedByTag.length === 0) return null;
  const max = Math.max(...solvedByTag.map((it) => it.solved));

  return (
    <Card className="space-y-2.5 p-5">
      <CardTitle>알고리즘 분류별 푼 문제</CardTitle>
      <ul className="space-y-1.5">
        {solvedByTag.map((entry) => (
          <li key={entry.slug} className="flex items-center gap-3">
            {/* 그 분류의 문제 목록으로 바로 갈 수 있어야 다음에 풀 것을 고를 수 있다. */}
            <a
              href={`/problems?tag=${entry.slug}`}
              className="w-28 shrink-0 truncate text-xs text-ink transition hover:text-brand"
            >
              {entry.name}
            </a>
            <span
              className="h-2 rounded-full bg-brand/60"
              style={{ width: `${(entry.solved / max) * 60}%` }}
            />
            <span className="text-xs text-ink-muted">{entry.solved}문제</span>
          </li>
        ))}
      </ul>
    </Card>
  );
}
