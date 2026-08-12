import type { Difficulty } from "@/entities/problem";

/** 문제집 (#87). */

/**
 * 공개 범위.
 *
 * **1차에는 공개 목록이 없다.** 누구나 공개 문제집을 만들 수 있게 하면 스팸·중복·방치가
 * 즉시 생기는데 지금은 신고도 정리도 할 수 없다.
 */
export type CollectionVisibility = "PRIVATE" | "UNLISTED";

export const VISIBILITY_LABELS: Record<CollectionVisibility, string> = {
  PRIVATE: "나만 보기",
  UNLISTED: "링크가 있는 사람만",
};

export interface CollectionSummary {
  id: number;
  name: string;
  description: string;
  visibility: CollectionVisibility;
  visibilityLabel: string;
  /** 주인에게만 내려온다. 링크 공유 주소를 만드는 데 쓴다. */
  shareToken: string | null;
  problemCount: number;
  solvedCount: number;
  ownerNickname: string;
  createdAt: string;
}

export interface CollectionProblem {
  /** 문제 번호 (#204). 주소가 번호로 간다. */
  id: number;
  slug: string;
  title: string;
  /** 화면이 난이도 뱃지를 그대로 쓰도록 티어가 아니라 난이도가 온다. */
  difficulty: Difficulty;
  difficultyLabel: string;
  solved: boolean;
}

export interface CollectionDetail {
  summary: CollectionSummary;
  editable: boolean;
  problems: CollectionProblem[];
}

/** 공유하려면 문제가 이만큼 있어야 한다. 1개짜리 묶음은 묶음이 아니다. */
export const MIN_SHARED_PROBLEMS = 2;
