import type { ReactNode } from "react";

/**
 * 아바타 (#116).
 *
 * **기본 표현은 닉네임 첫 글자 + 색이다.** 회색 실루엣 하나로 두면 목록에서 모두가
 * 똑같아 보이는데, 아바타를 넣은 이유가 바로 **사람을 구분하려는 것**이다.
 * 대부분의 사용자는 이미지를 올리지 않으므로, 기본 표현이 실제로 쓰이는 표현이다.
 */
const PALETTE = [
  "bg-rose-500/20 text-rose-600 dark:text-rose-400",
  "bg-amber-500/20 text-amber-700 dark:text-amber-400",
  "bg-emerald-500/20 text-emerald-700 dark:text-emerald-400",
  "bg-sky-500/20 text-sky-700 dark:text-sky-400",
  "bg-violet-500/20 text-violet-700 dark:text-violet-400",
  "bg-teal-500/20 text-teal-700 dark:text-teal-400",
];

const SIZES = {
  sm: "h-6 w-6 text-[10px]",
  md: "h-9 w-9 text-sm",
  lg: "h-20 w-20 text-2xl",
} as const;

export function Avatar({
  nickname,
  avatarUrl,
  size = "md",
}: {
  nickname: string;
  avatarUrl?: string | null;
  size?: keyof typeof SIZES;
}) {
  const shape = `inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full ${SIZES[size]}`;

  if (avatarUrl) {
    return (
      // 서버가 정사각 PNG 로 다시 만들어 주므로(#115) 비율이 깨질 일이 없다.
      // eslint-disable-next-line @next/next/no-img-element
      <img src={avatarUrl} alt="" aria-hidden className={`${shape} object-cover`} />
    );
  }

  return (
    <span className={`${shape} font-bold ${colorOf(nickname)}`} aria-hidden>
      {initialOf(nickname)}
    </span>
  );
}

/**
 * 닉네임에서 색을 정한다.
 *
 * **같은 사람은 늘 같은 색이어야 한다.** 무작위로 고르면 새로고침마다 색이 바뀌어
 * 구분에 아무 도움이 되지 않는다.
 */
function colorOf(nickname: string): string {
  // FNV-1a 에 마지막 섞기(avalanche)를 더한다.
  //
  // 단순히 코드 포인트를 더하면 **한 글자 닉네임이 한 색으로 몰린다** — 한글 음절은
  // 코드 포인트가 촘촘히 붙어 있어서, 나머지 연산만으로는 흩어지지 않는다.
  let hash = 0x811c9dc5;
  for (const char of nickname) {
    hash ^= char.codePointAt(0)!;
    hash = Math.imul(hash, 0x01000193);
  }
  hash ^= hash >>> 15;
  hash = Math.imul(hash, 0x2545f491);
  hash ^= hash >>> 13;

  return PALETTE[Math.abs(hash) % PALETTE.length];
}

/** 첫 글자. 한글·영문·이모지 모두 한 글자로 잘린다 (코드 포인트 기준). */
function initialOf(nickname: string): ReactNode {
  return [...nickname][0]?.toUpperCase() ?? "?";
}

export { colorOf as avatarColorOf, initialOf as avatarInitialOf };
