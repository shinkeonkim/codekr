import Image from "next/image";

/** 원본 비율. 높이를 정하면 폭은 여기서 나온다. */
const RATIO = 440 / 234;

/**
 * 워드마크 (#261).
 *
 * **두 배경 모두에서 읽히는 판을 쓴다.** 처음 받은 워드마크는 "코드" 가 진한 남색이라
 * 어두운 배경에서 사라졌다 — 이 사이트는 라이트·다크를 모두 지원하므로 그것으로는
 * 헤더에 걸 수 없었다. 지금 것은 중간 밝기의 파랑이라 흰 배경에서도, 어두운 배경에서도
 * 읽힌다 (두 배경에 얹어 확인했다).
 *
 * 심벌을 옆에 함께 두지 않는다 — 워드마크의 "드" 안에 이미 `</>` 가 들어 있어,
 * 나란히 놓으면 같은 표시가 두 번 나온다.
 */
export function BrandWordmark({ height = 28, className = "" }: { height?: number; className?: string }) {
  return (
    <Image
      src="/brand/wordmark.webp"
      // 이 그림이 곧 사이트 이름이다. 링크의 이름이 되므로 비우면 안 된다.
      alt="코드.kr"
      width={Math.round(height * RATIO)}
      height={height}
      priority
      className={`select-none ${className}`}
    />
  );
}
