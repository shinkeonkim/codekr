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

  /*
    상황 그림 (#261). 위의 다섯이 "표정" 이라면 아래는 **그 자리에만 쓰는 장면**이다.

    원본에는 그림 안에 안내판과 버튼이 그려져 있었다 — 500 의 "TRY AGAIN", 403 의
    "로그인 하기" 같은 것들. **그 부분은 잘라 냈다.** 눌리지 않는 버튼 그림은 함정이고,
    안내 문구는 화면이 글자로 말해야 화면 폭과 스크린 리더에 대응한다.
  */
  /** 404 만 장면 전체를 쓴다 — 가짜 버튼이 없고, 표지판·안내판이 상황을 설명해 준다. */
  notFound: { src: "/brand/state-not-found.webp", width: 520, height: 492 },
  serverError: { src: "/brand/state-server-error.webp", width: 460, height: 671 },
  forbidden: { src: "/brand/state-forbidden.webp", width: 460, height: 372 },
  welcome: { src: "/brand/state-welcome.webp", width: 460, height: 473 },
  goodbye: { src: "/brand/state-goodbye.webp", width: 460, height: 427 },
  award: { src: "/brand/state-award.webp", width: 460, height: 347 },
  study: { src: "/brand/state-study.webp", width: 520, height: 333 },
  /** 가로로 납작한 구도라 진행 막대 옆에 놓아도 줄이 밀리지 않는다. */
  working: { src: "/brand/state-working.webp", width: 560, height: 360 },
  /** 고양이만. 사람이 들어가기엔 좁은 자리에 쓴다. */
  cat: { src: "/brand/mascot-cat.webp", width: 320, height: 498 },
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
