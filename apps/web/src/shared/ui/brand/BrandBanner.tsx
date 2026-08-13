import Image from "next/image";

/**
 * 화면 맨 위에 까는 가로 배너 (#461).
 *
 * [BrandCharacter] 가 "빈 자리를 채우는 그림" 이라면 이쪽은 **목록 위에 까는 띠**다.
 * 셋 다 원본이 1983×793 이라 비율이 같다 — 슬라이드쇼로 갈아 끼워도 높이가 안 튄다.
 */
const BANNERS = {
  /** 전체 제출 1번째 — 코드를 쓰는 장면. */
  submissionsCoding: "/brand/banner-submissions-1.webp",
  /** 전체 제출 2번째 — 채점 결과를 읽는 장면. */
  submissionsReview: "/brand/banner-submissions-2.webp",
  /**
   * 어드민 첫 화면.
   *
   * **그림 속 가짜 UI 를 잘라내지 않았다.** #261 은 "눌리지 않는 버튼 그림은 함정" 이라
   * 잘라냈지만, 여기서는 그림이 **장식 배너**이고 실제 조작은 바로 아래 카드 격자가 한다.
   * 그림 속 숫자(12,543 · 62.4%)도 마찬가지로 실제 값이 아니다 — 진짜 지표를 이 자리에
   * 넣게 되면 그때 이 그림을 뺀다.
   */
  admin: "/brand/banner-admin.webp",
} as const;

export type BrandBannerName = keyof typeof BANNERS;

/** 원본 비율. 셋이 같아서 상수 하나로 둔다 — 어긋나면 슬라이드가 바뀔 때 아래 글이 밀린다. */
export const BANNER_WIDTH = 1600;
export const BANNER_HEIGHT = 640;

export function BrandBanner({
  name,
  /** 화면에 처음부터 보이는 자리면 켠다. 늦게 오면 아래 목록이 통째로 밀린다. */
  priority = false,
  className = "",
}: {
  name: BrandBannerName;
  priority?: boolean;
  className?: string;
}) {
  return (
    <Image
      src={BANNERS[name]}
      // 옆·아래의 제목이 이미 같은 것을 말한다. 그림을 다시 설명하면 같은 내용을 두 번 듣는다.
      alt=""
      width={BANNER_WIDTH}
      height={BANNER_HEIGHT}
      priority={priority}
      sizes="(max-width: 1152px) 100vw, 1152px"
      className={`w-full rounded-card border border-border ${className}`}
    />
  );
}
