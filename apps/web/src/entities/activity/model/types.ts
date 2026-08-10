/** 잔디형 활동 그래프가 쓰는 표현. */

export interface DailyActivity {
  date: string;
  count: number;
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
