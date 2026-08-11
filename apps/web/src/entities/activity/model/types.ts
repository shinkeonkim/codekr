/** 잔디형 활동 그래프가 쓰는 표현. */

export interface DailyActivity {
  date: string;
  count: number;
  /**
   * 그날 정답 판정을 받은 **서로 다른 문제 수** (#133).
   *
   * "그날 처음 맞힌 문제" 가 아니다 — 어제 푼 문제를 오늘 다시 맞혀도 세어진다.
   * 그래서 화면에서도 "새로 푼" 이 아니라 **"맞힌 문제"** 라고 부른다.
   */
  solvedCount: number;
}

export interface ActivityResponse {
  from: string;
  to: string;
  days: DailyActivity[];
  totalCount: number;
  activeDayCount: number;
  /** 스트릭은 조회 범위가 아니라 전체 기간 기준이다 (#81). */
  currentStreak: number;
  longestStreak: number;
  /** 활동이 있는 연도 + 올해. 최신 연도가 앞. */
  availableYears: number[];
  timeZone: string;
}
