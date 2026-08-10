"use client";

import { useEffect, useState } from "react";
import { problemApi } from "../api";
import type { ProblemDetail } from "./types";

/** 문제 상세를 읽어 오는 공통 훅. 세 탭이 같은 방식으로 문제를 가져온다. */
export function useProblem(slug: string) {
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    problemApi
      .detail(slug)
      .then(setProblem)
      .catch(() => setError("문제를 찾을 수 없습니다."));
  }, [slug]);

  return { problem, error };
}
