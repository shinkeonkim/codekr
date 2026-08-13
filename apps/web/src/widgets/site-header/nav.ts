/**
 * 헤더 내비 항목과 활성 판정 (#182).
 *
 * **접두사 비교만으로는 두 항목이 동시에 켜진다.** `/submissions/explore` 는
 * `/submissions` 로도 시작하므로 "전체 제출"과 "내 제출"이 함께 활성이 됐다.
 * 경로가 다른 경로의 접두사인 한 계속 생기는 문제라, 규칙을 한 곳에 두고 시험으로 고정한다.
 */
export interface NavItem {
  href: string;
  label: string;
}

export const NAV_ITEMS: NavItem[] = [
  { href: "/problems", label: "문제" },
  { href: "/submissions/explore", label: "전체 제출" },
  { href: "/posts", label: "게시판" },
  { href: "/collections", label: "문제집" },
  // 남이 만든 것을 발견하는 자리 (#208). 내 문제집과 다른 화면이다.
  { href: "/collections/explore", label: "공개 문제집" },
  { href: "/contests", label: "대회" },
  { href: "/ranking", label: "랭킹" },
  // 그룹은 **소속과 다른 것이다** (#401) — 같은 목록에 섞지 않는다.
  { href: "/groups", label: "그룹" },
  { href: "/submissions", label: "내 제출" },
];

/**
 * 지금 활성인 항목의 href. 없으면 null.
 *
 * **가장 길게 일치하는 것 하나만** 고른다 — 겹치는 경로에서 더 구체적인 쪽이 이긴다.
 */
export function activeHref(pathname: string, items: NavItem[] = NAV_ITEMS): string | null {
  const matched = items
    .filter((item) => pathname === item.href || pathname.startsWith(`${item.href}/`))
    .sort((a, b) => b.href.length - a.href.length);

  return matched[0]?.href ?? null;
}
