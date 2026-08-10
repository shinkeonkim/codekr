import Link from "next/link";

/**
 * 닉네임을 프로필로 잇는 링크 (#83).
 *
 * 목록 어디서든 같은 방식으로 쓰기 위해 한 곳에 둔다 — 주소 규칙이 바뀌어도
 * 여기만 고치면 된다.
 */
export function UserLink({ nickname, className = "" }: { nickname: string; className?: string }) {
  return (
    <Link
      href={`/users/${encodeURIComponent(nickname)}`}
      className={`hover:text-brand hover:underline ${className}`}
    >
      {nickname}
    </Link>
  );
}
