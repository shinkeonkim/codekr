import { reachableRoles } from "@/entities/user";
import type { UserRole } from "@/entities/user";

/**
 * 어드민 구획 (#131).
 *
 * **항목 추가가 배열 한 줄이 되게 데이터로 선언한다.** 앞으로 붙을 것이 많다 —
 * 대회 운영(#61~#63), 실행기 스케일(#40), 역할 관리(#103).
 */
export interface AdminNavItem {
  href: string;
  label: string;
  description: string;
  /**
   * 이 항목을 볼 수 있는 역할. 비우면 어드민 영역에 들어온 누구나 본다.
   *
   * **감추는 것은 안내이지 방어가 아니다.** 서버의 경로 규칙이 최종 방어선이다.
   * 그래도 감추는 이유: 눌렀을 때 403 이 나는 링크는 고장으로 보인다.
   */
  roles?: UserRole[];
}

export const ADMIN_NAV: AdminNavItem[] = [
  {
    href: "/admin/problems",
    label: "문제 관리",
    description: "문제 등록·수정과 정답 검증",
    roles: ["PROBLEM_SETTER"],
  },
  {
    href: "/admin/stats",
    label: "통계",
    description: "제출·가입 추세와 판정 분포",
    // 읽기만 하지만 아무나 볼 것은 아니다 — 가입 추세와 채점 실패율은 우리가 얼마나
    // 아픈지를 그대로 드러낸다 (#550).
    roles: ["ADMIN"],
  },
  {
    href: "/admin/queues",
    label: "큐 모니터링",
    description: "채점 적체와 실행기 수",
    roles: ["ADMIN"],
  },
  {
    href: "/admin/users",
    label: "회원 관리",
    description: "회원 찾기·역할 변경·강제 탈퇴",
    // 찾는 것은 ADMIN 까지 열린다 (#223). 역할 변경·강제 탈퇴는 서버가 SUPERUSER 로
    // 막고, 화면도 그 버튼만 감춘다 — 목록 자체를 감추면 어드민이 회원을 못 찾는다.
    roles: ["ADMIN"],
  },
  {
    href: "/admin/audit-logs",
    label: "관리 기록",
    description: "역할 변경·강제 탈퇴의 자취",
    // 어드민끼리 서로를 보는 것이라 최고 관리자만이다 (#225).
    roles: ["SUPERUSER"],
  },
  {
    href: "/admin/contests",
    label: "대회 관리",
    description: "대회 등록·수정과 공개",
    roles: ["CONTEST_MANAGER"],
  },
  {
    href: "/admin/board",
    label: "게시판 관리",
    description: "글·댓글 훑어보기와 내리기",
    // 공지를 쓰고 남의 글을 내리는 역할이다 (#336).
    roles: ["BOARD_MANAGER"],
  },
  {
    href: "/admin/collections",
    label: "문제집 관리",
    description: "남에게 보여지는 문제집 훑어보기와 내리기",
    // 게시판과 같은 결의 일이다 (#393). 새 역할을 만들지 않았다 — 지금 여섯인데
    // 쓸 화면이 없는 것이 이미 있었다 (#103).
    roles: ["ADMIN"],
  },
  {
    href: "/admin/affiliations",
    label: "소속 관리",
    description: "학교·회사와 그 메일 도메인",
    // **잘못 넣으면 그 도메인을 가진 모두가 그 소속을 얻는다** (#397). 서버도 ADMIN 이다.
    roles: ["ADMIN"],
  },
  {
    href: "/admin/groups",
    label: "그룹 관리",
    description: "누구나 만드는 그룹 훑어보기와 내리기",
    // 문제집(#393)과 같은 결이다 — 새 역할을 만들지 않았다 (#438).
    roles: ["ADMIN"],
  },
  {
    href: "/admin/badges",
    label: "뱃지",
    description: "문구·노출과 달성 규칙",
    // 뱃지는 모두에게 보이는 것이라 아무 어드민이나 바꾸면 안 된다 (#203).
    roles: ["SUPERUSER"],
  },
  {
    href: "/admin/problem-reports",
    label: "오류 신고",
    description: "사용자가 알린 지문·데이터 문제",
    // 문제를 고치는 일이라 문제 출제자가 본다 (#478).
    roles: ["PROBLEM_SETTER"],
  },
  {
    href: "/admin/operations",
    label: "운영 작업",
    description: "랭킹·활동 집계 재계산",
    // 집계를 통째로 다시 만드는 일이라 최고 관리자만 본다 — 서버 경로 규칙과 같다 (#103).
    roles: ["SUPERUSER"],
  },
];

export function visibleNav(roles: UserRole[]): AdminNavItem[] {
  // 위계는 `entities/user` 가 안다 (#544) — 보는 곳이 여기 하나가 아니라 두 벌이 되면 갈라진다.
  const reachable = reachableRoles(roles);
  return ADMIN_NAV.filter((item) => !item.roles || item.roles.some((role) => reachable.has(role)));
}
