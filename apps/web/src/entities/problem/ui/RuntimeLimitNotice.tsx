import type { ProblemDetail } from "../model/types";

/**
 * 언어별 제한이 따로 있는 문제에서 그 사실을 알린다 (#97).
 *
 * 문제 기본 제한만 보여주면, 자기가 쓸 언어에 실제로 적용되는 값과 다른 숫자를 보고
 * 코드를 짜게 된다. 오버라이드가 하나도 없으면 아무것도 그리지 않는다 —
 * 대부분의 문제에서 이 줄은 소음이다.
 */
export function RuntimeLimitNotice({ problem }: { problem: ProblemDetail }) {
  const overridden = problem.runtimes.filter((runtime) => runtime.limitOverridden);
  if (overridden.length === 0) return null;

  return (
    <div className="rounded-lg border border-warn/30 bg-warn/8 px-4 py-3 text-sm">
      <p className="font-medium text-ink">언어별 제한이 다릅니다</p>
      <ul className="mt-1.5 space-y-0.5 text-xs text-ink-muted">
        {overridden.map((runtime) => (
          <li key={runtime.id}>
            {runtime.label} — 시간 {runtime.timeLimitMs}ms · 메모리 {runtime.memoryLimitMb}MB
          </li>
        ))}
      </ul>
      <p className="mt-1.5 text-xs text-ink-muted">
        그 밖의 언어는 시간 {problem.timeLimitMs}ms · 메모리 {problem.memoryLimitMb}MB 입니다.
      </p>
    </div>
  );
}
