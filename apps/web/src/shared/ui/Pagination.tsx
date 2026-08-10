"use client";

import { Button } from "./primitives";

interface Props {
  page: number;
  totalPages: number;
  totalElements: number;
  onChange: (page: number) => void;
}

/** 현재 페이지 주변으로 최대 5개만 보여준다. 페이지가 많아도 줄이 넘치지 않게. */
const WINDOW = 5;

function windowOf(page: number, totalPages: number): number[] {
  const start = Math.max(0, Math.min(page - Math.floor(WINDOW / 2), totalPages - WINDOW));
  const end = Math.min(totalPages, start + WINDOW);
  return Array.from({ length: end - Math.max(0, start) }, (_, i) => Math.max(0, start) + i);
}

/**
 * 목록 페이지 이동 (#77).
 *
 * 한 페이지에 다 들어가면 렌더하지 않는다 — 누를 데가 없는 컨트롤은 자리만 차지한다.
 * 전체 건수를 함께 보여주는 이유는 "얼마나 되는지"가 페이지 번호보다 먼저 궁금하기 때문이다.
 */
export function Pagination({ page, totalPages, totalElements, onChange }: Props) {
  if (totalPages <= 1) return null;

  return (
    <nav className="flex flex-wrap items-center justify-between gap-3 pt-2" aria-label="페이지">
      <p className="text-xs text-ink-muted">
        총 {totalElements.toLocaleString("ko-KR")}건 · {page + 1}/{totalPages} 페이지
      </p>

      <div className="flex items-center gap-1">
        <Button variant="ghost" onClick={() => onChange(page - 1)} disabled={page === 0}>
          이전
        </Button>
        {windowOf(page, totalPages).map((it) => (
          <Button
            key={it}
            variant={it === page ? "primary" : "ghost"}
            onClick={() => onChange(it)}
            aria-current={it === page ? "page" : undefined}
            className="min-w-9 px-3"
          >
            {it + 1}
          </Button>
        ))}
        <Button variant="ghost" onClick={() => onChange(page + 1)} disabled={page >= totalPages - 1}>
          다음
        </Button>
      </div>
    </nav>
  );
}
