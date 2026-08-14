import type { UserRole } from "./types";

/**
 * 역할 위계 (#103). 서버의 `RoleHierarchy` 와 같은 모양이다.
 *
 * 화면이 이것을 아는 이유는 **감출지 말지를 정하기 위해서**다. 권한 판단은 서버가 한다.
 *
 * `entities/user` 에 두는 이유: 역할은 사용자의 것이고, 이것을 보는 곳이 어드민 내비
 * 하나가 아니기 때문이다 (#544 의 대회 공지 삭제도 본다). **두 벌이 되면 갈라진다.**
 */
const IMPLIES: Partial<Record<UserRole, UserRole[]>> = {
  SUPERUSER: ["ADMIN", "PROBLEM_SETTER", "CONTEST_MANAGER", "BOARD_MANAGER"],
  ADMIN: ["PROBLEM_SETTER", "CONTEST_MANAGER", "BOARD_MANAGER"],
};

/** 가진 역할에서 위계를 따라 닿을 수 있는 역할 전부. */
export function reachableRoles(roles: UserRole[]): Set<UserRole> {
  const reachable = new Set<UserRole>(roles);
  roles.forEach((role) => IMPLIES[role]?.forEach((implied) => reachable.add(implied)));
  return reachable;
}

export function hasRole(roles: UserRole[] | undefined, required: UserRole): boolean {
  return roles !== undefined && reachableRoles(roles).has(required);
}
