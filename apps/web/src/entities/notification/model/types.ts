/** 알림 카테고리 (#106). 서버가 목록과 라벨을 함께 내려 화면이 하드코딩하지 않는다. */
export type NotificationCategory = "JUDGE" | "CONTEST" | "SYSTEM";

export interface NotificationCategoryOption {
  category: NotificationCategory;
  label: string;
}

/**
 * 알림 (#106).
 *
 * 토스트(#112)와 다르다 — 토스트는 내가 방금 한 행동의 결과이고, 이것은 내가 없을 때
 * 서버에서 일어난 일이다.
 */
export interface Notification {
  id: number;
  category: NotificationCategory;
  categoryLabel: string;
  title: string;
  body: string | null;
  link: string | null;
  read: boolean;
  createdAt: string;
}
