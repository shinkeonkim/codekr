/**
 * 목록 아래에 무엇을 보여줄지 정한다 (#77, #181).
 *
 * **총 건수는 페이지가 하나여도 보여준다.** 전에는 `totalPages <= 1` 이면 통째로
 * 아무것도 그리지 않았는데, 그러면 목록이 한 페이지에 다 들어간 것인지 페이지 이동이
 * 빠진 것인지 화면만 봐서는 구분되지 않는다 — 실제로 "페이지네이션이 없다"는 오해를 샀다.
 *
 * 누를 데가 없는 **버튼만** 감춘다. "얼마나 되는지"는 페이지가 몇 개든 궁금한 것이다.
 */
export interface PaginationState {
  page: number;
  totalPages: number;
  totalElements: number;
}

export interface PaginationView {
  visible: boolean;
  summary: string;
  showButtons: boolean;
}

export function paginationView({ page, totalPages, totalElements }: PaginationState): PaginationView {
  const showButtons = totalPages > 1;
  const count = `총 ${totalElements.toLocaleString("ko-KR")}건`;

  return {
    visible: totalElements > 0,
    summary: showButtons ? `${count} · ${page + 1}/${totalPages} 페이지` : count,
    showButtons,
  };
}
