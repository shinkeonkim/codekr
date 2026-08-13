"use client";

import { Button } from "./button";
import { paginationView } from "./paginationRule";

interface Props {
  page: number;
  totalPages: number;
  totalElements: number;
  onChange: (page: number) => void;
}

/** 현재 페이지 주변으로 최대 5개만 보여준다. 페이지가 많아도 줄이 넘치지 않게. */
const WINDOW = 5;

function windowOf(page: number, totalPages: number): number[] {
  const start = Math.max(
    0,
    Math.min(page - Math.floor(WINDOW / 2), totalPages - WINDOW),
  );
  const end = Math.min(totalPages, start + WINDOW);
  return Array.from(
    { length: end - Math.max(0, start) },
    (_, i) => Math.max(0, start) + i,
  );
}

/**
 * 목록 페이지 이동 (#77, #181).
 *
 * **총 건수는 페이지가 하나여도 보여준다.** 전에는 `totalPages <= 1` 이면 통째로
 * 아무것도 그리지 않았는데, 그러면 목록이 한 페이지에 다 들어간 것인지 페이지 이동이
 * 빠진 것인지 화면만 봐서는 구분되지 않는다 — 실제로 "페이지네이션이 없다"는 오해를 샀다.
 *
 * 누를 데가 없는 **버튼만** 감춘다. "얼마나 되는지"는 페이지가 몇 개든 궁금한 것이다.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  onChange,
}: Props) {
  const view = paginationView({ page, totalPages, totalElements });
  if (!view.visible) return null;

  return (
    <nav
      className="flex flex-wrap items-center justify-between gap-3 pt-2"
      aria-label="페이지"
    >
      <p className="text-xs text-ink-muted">{view.summary}</p>

      {view.showButtons ? (
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            onClick={() => onChange(page - 1)}
            disabled={page === 0}
          >
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
          <Button
            variant="ghost"
            onClick={() => onChange(page + 1)}
            disabled={page >= totalPages - 1}
          >
            다음
          </Button>
        </div>
      ) : null}
    </nav>
  );
}
