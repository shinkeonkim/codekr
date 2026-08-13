"use client";

import { useState } from "react";
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
  /**
   * 색을 뽑을 값 (#307). 없으면 이름에서 뽑는다.
   *
   * **이름을 바꿔도 색이 그대로여야 한다** — 색은 목록에서 사람을 구분하는 단서인데,
   * 이름이 바뀔 때마다 색이 바뀌면 그 단서가 사라진다. 주소(`handle`)는 안 바뀐다.
   */
  colorKey,
}: {
  nickname: string;
  avatarUrl?: string | null;
  size?: keyof typeof SIZES;
  colorKey?: string;
}) {
  const shape = `inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full ${SIZES[size]}`;

  /*
    **이미지가 실패하면 기본 표현으로 떨어진다** (#314).

    전에는 `<img>` 를 그리고 끝이라, 주소가 틀리거나 저장소가 안 붙으면 깨진 이미지
    아이콘이 목록마다 박혔다. 대신 고장이 눈에 덜 띄게 되는 대가가 있는데, 그것은
    화면이 아니라 저장소 상태로 알아야 하는 것이라고 본다.

    주소가 바뀌면 다시 시도해야 하므로 실패한 주소를 기억한다 — 단순한 불리언이면
    새 아바타를 올린 뒤에도 계속 기본 표현으로 남는다.
  */
  const [failedUrl, setFailedUrl] = useState<string | null>(null);

  if (avatarUrl && avatarUrl !== failedUrl) {
    return (
      // 서버가 정사각 PNG 로 다시 만들어 주므로(#115) 비율이 깨질 일이 없다.
      // 옆에 늘 이름이 함께 있으므로(UserLink) 그림 자체는 장식이다 — alt 는 비운다.
      // eslint-disable-next-line @next/next/no-img-element
      <img
        src={avatarUrl}
        alt=""
        aria-hidden
        className={`${shape} object-cover`}
        onError={() => setFailedUrl(avatarUrl)}
      />
    );
  }

  return (
    <span className={`${shape} font-bold ${colorOf(colorKey ?? nickname)}`} aria-hidden>
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
