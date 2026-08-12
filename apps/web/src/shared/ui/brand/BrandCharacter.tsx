import Image from "next/image";

/**
 * 상황별 캐릭터 (#261).
 *
 * **잠깐 멈추는 자리에만 쓴다.** 빈 화면·채점 결과처럼 사용자가 다음 할 일을 정하는
 * 순간이 그런 자리다. 문제를 푸는 화면에는 넣지 않는다 — 거기서는 캐릭터가 방해다.
 *
 * 원본 11종을 다 쓰지 않는다. 앞·옆·뒤 3면은 캐릭터 시트용이지 화면용이 아니다.
 */
const CHARACTERS = {
  /** 아직 아무것도 없는 자리. "여기서 이런 걸 한다" 를 말없이 보여준다. */
  laptop: { src: "/brand/character-laptop.webp", width: 480, height: 326 },
  /** 채점을 기다리는 동안. */
  thinking: { src: "/brand/character-thinking.webp", width: 480, height: 338 },
  /** 맞았습니다. */
  success: { src: "/brand/character-success.webp", width: 480, height: 372 },
  /** 틀렸습니다. **놀리지 않는 표정을 고른다** — 틀린 사람에게 웃는 그림을 보이지 않는다. */
  fail: { src: "/brand/character-fail.webp", width: 480, height: 375 },
  /** 처음 맞힌 순간처럼 크게 축하할 때만. 자주 쓰면 축하가 아니게 된다. */
  celebration: { src: "/brand/character-celebration.webp", width: 480, height: 334 },
} as const;

export type BrandCharacterName = keyof typeof CHARACTERS;

export function BrandCharacter({
  name,
  size = 160,
  /**
   * 그림이 **혼자 뜻을 지닐 때만** 적는다.
   *
   * 옆에 같은 뜻의 글자가 있으면 비워 둔다 — 스크린 리더가 같은 내용을 두 번 읽는다.
   */
  alt = "",
  className = "",
}: {
  name: BrandCharacterName;
  size?: number;
  alt?: string;
  className?: string;
}) {
  const character = CHARACTERS[name];
  return (
    <Image
      src={character.src}
      alt={alt}
      width={character.width}
      height={character.height}
      // 폭만 정하고 높이는 비율로 둔다 — 그림마다 세로가 다르다.
      style={{ width: size, height: "auto" }}
      className={`select-none ${className}`}
    />
  );
}
