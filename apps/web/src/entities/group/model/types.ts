/** 그룹 (#401, #240 6단계). **소속과 다른 것이다** — 누구나 만들고 사람이 사람을 부른다. */
export interface GroupSummary {
  id: number;
  name: string;
  memberCount: number;
  owner: boolean;
}

export interface GroupMember {
  userId: number;
  nickname: string;
  handle: string;
  owner: boolean;
  joinedAt: string;
}

export interface GroupDetail {
  id: number;
  name: string;
  description: string;
  openJoin: boolean;
  memberCount: number;
  memberLimit: number;
  owner: boolean;
  member: boolean;
  /** **방장에게만 온다.** 멤버 아무나 부르면 방장이 인원을 통제할 길이 없다. */
  inviteToken: string | null;
  members: GroupMember[];
}

/** 초대 링크를 눌렀을 때 **가입 전에** 보여 줄 것. */
export interface GroupInvitePreview {
  id: number;
  name: string;
  description: string;
  memberCount: number;
  member: boolean;
}

/** 어드민이 보는 그룹 한 줄 (#438). **명단은 담기지 않는다** — 그 안의 일이다. */
export interface AdminGroupRow {
  id: number;
  name: string;
  description: string;
  ownerNickname: string;
  memberCount: number;
  openJoin: boolean;
  createdAt: string;
}
