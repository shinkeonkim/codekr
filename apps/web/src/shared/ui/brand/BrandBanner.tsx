import Image from "next/image";

/**
 * 화면 맨 위에 까는 가로 배너 (#461).
 *
 * [BrandCharacter] 가 "빈 자리를 채우는 그림" 이라면 이쪽은 **목록 위에 까는 띠**다.
 *
 * 크기를 이름마다 따로 적는다 (#518). 전에는 원본이 다 1983×793 이라 상수 하나로 뒀는데,
 * **슬라이드 셋만 위쪽을 잘라 내면서 어드민 배너와 비율이 갈렸다.**
 */
const BANNERS = {
  /** 전체 제출 1번째 — 코드를 쓰는 장면. */
  submissionsCoding: { src: "/brand/banner-submissions-1.webp", width: 1600, height: 427 },
  /** 전체 제출 2번째 — 채점 결과를 읽는 장면. */
  submissionsReview: { src: "/brand/banner-submissions-2.webp", width: 1600, height: 427 },
  /** 전체 제출 3번째 — 풀고 나서 기뻐하는 장면. */
  submissionsSolved: { src: "/brand/banner-submissions-3.webp", width: 1600, height: 427 },
  /**
   * 어드민 첫 화면.
   *
   * **그림 속 가짜 UI 를 잘라내지 않았다.** #261 은 "눌리지 않는 버튼 그림은 함정" 이라
   * 잘라냈지만, 여기서는 그림이 **장식 배너**이고 실제 조작은 바로 아래 카드 격자가 한다.
   * 그림 속 숫자(12,543 · 62.4%)도 마찬가지로 실제 값이 아니다 — 진짜 지표를 이 자리에
   * 넣게 되면 그때 이 그림을 뺀다.
   *
   * **위쪽을 자르지 않은 것도 그래서다** (#518). 슬라이드 셋은 위가 빈 하늘이라 잘랐지만,
   * 이 그림은 위쪽에 "관리의 시작," 이 박혀 있어 같은 만큼 자르면 그 글자가 잘린다.
   */
  admin: { src: "/brand/banner-admin.webp", width: 1600, height: 640 },
} as const;

export type BrandBannerName = keyof typeof BANNERS;

/** 슬라이드쇼가 높이를 미리 잡을 때 쓴다 — 그림이 오기 전에 자리를 비워 두어야 한다. */
export function bannerRatio(name: BrandBannerName): string {
  const banner = BANNERS[name];
  return `${banner.width} / ${banner.height}`;
}

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
  const banner = BANNERS[name];
  return (
    <Image
      src={banner.src}
      // 옆·아래의 제목이 이미 같은 것을 말한다. 그림을 다시 설명하면 같은 내용을 두 번 듣는다.
      alt=""
      width={banner.width}
      height={banner.height}
      priority={priority}
      sizes="(max-width: 1152px) 100vw, 1152px"
      className={`w-full rounded-card border border-border ${className}`}
    />
  );
}
