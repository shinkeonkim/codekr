import Image from "next/image";

/**
 * 헤더의 심벌 (#261).
 *
 * **테마마다 다른 파일을 쓴다.** 심벌은 원판 위에 `</>` 를 얹은 모양이라, 어두운 원판은
 * 어두운 배경에서 테두리가 사라지고 밝은 원판은 밝은 배경에서 그렇다. 색을 필터로
 * 뒤집으면 파란 `</>` 까지 함께 뒤집혀 브랜드 색이 아니게 된다.
 *
 * 그래서 두 장을 겹쳐 두고 CSS 로 고른다 — 자바스크립트가 테마를 알기 전에도 맞는
 * 것이 보인다(깜빡임 없음).
 */
export function BrandSymbol({ size = 26 }: { size?: number }) {
  return (
    <span className="relative inline-block shrink-0" style={{ width: size, height: size }}>
      <Image
        src="/brand/symbol-dark.webp"
        alt=""
        width={size}
        height={size}
        className="block dark:hidden"
        priority
      />
      <Image
        src="/brand/symbol-light.webp"
        alt=""
        width={size}
        height={size}
        className="hidden dark:block"
        priority
      />
    </span>
  );
}
