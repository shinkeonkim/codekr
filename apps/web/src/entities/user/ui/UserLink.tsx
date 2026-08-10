import Link from "next/link";
import { Avatar } from "./Avatar";

/**
 * 닉네임을 프로필로 잇는 링크 (#83).
 *
 * 목록 어디서든 같은 방식으로 쓰기 위해 한 곳에 둔다 — 주소 규칙이 바뀌어도
 * 여기만 고치면 된다.
 */
export function UserLink({
  nickname,
  avatarUrl,
  className = "",
  showAvatar = false,
}: {
  nickname: string;
  avatarUrl?: string | null;
  className?: string;
  /** 목록에서 사람을 눈으로 구분해야 할 때 켠다 (#116). 문장 안에서는 끈다. */
  showAvatar?: boolean;
}) {
  return (
    <Link
      href={`/users/${encodeURIComponent(nickname)}`}
      className={`inline-flex items-center gap-1.5 hover:text-brand hover:underline ${className}`}
    >
      {showAvatar ? <Avatar nickname={nickname} avatarUrl={avatarUrl} size="sm" /> : null}
      {nickname}
    </Link>
  );
}
