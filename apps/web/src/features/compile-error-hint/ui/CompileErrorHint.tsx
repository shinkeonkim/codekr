"use client";

import { problemApi } from "@/entities/problem";
import type { ProblemDetail } from "@/entities/problem";
import { findErrorLocation } from "@/shared/lib";
import Link from "next/link";
import { useEffect, useState } from "react";

/**
 * 컴파일 오류가 **어느 파일의 몇 번 줄**인지 알려 준다 (#457, #498).
 *
 * 파일이 하나일 때는 필요 없던 것이다. 여럿이면 `Helper.java:17` 이 어느 탭의 이야기인지
 * 사용자가 눈으로 찾아야 하는데, 그 순간 **오류를 읽는 일이 문제 풀이보다 어려워진다.**
 *
 * **못 찾으면 아무것도 그리지 않는다.** 틀린 자리를 가리키는 것은 침묵보다 나쁘다.
 */
export function CompileErrorHint({
  compileError,
  problemSlug,
  runtimeId,
}: {
  compileError: string | null | undefined;
  problemSlug: string;
  runtimeId: string;
}) {
  const [problem, setProblem] = useState<ProblemDetail | null>(null);

  useEffect(() => {
    // 컴파일 오류가 있을 때만 문제를 읽는다 — 파일 목록은 그때만 쓸모가 있다.
    if (!compileError) return;
    let alive = true;
    problemApi
      .detail(problemSlug)
      .then((next) => alive && setProblem(next))
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, [compileError, problemSlug]);

  const files =
    problem?.runtimes.find((it) => it.id === runtimeId)?.files ?? [];
  if (!compileError || files.length === 0) return null;

  const location = findErrorLocation(
    compileError,
    files.map((file) => file.name),
  );
  if (!location) return null;

  return (
    <p className="text-xs text-ink-muted">
      <Link
        href={`/problems/${problemSlug}?file=${encodeURIComponent(location.file)}`}
        className="text-brand hover:underline"
      >
        {location.file}
        {location.line === null ? "" : ` ${location.line}번 줄`}
      </Link>
      {" 에서 난 오류입니다."}
    </p>
  );
}
