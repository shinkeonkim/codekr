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
  currentStreak: number;
  longestStreak: number;
  timeZone: string;
}
