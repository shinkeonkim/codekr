import { request } from "@/shared/api";

/** 무엇이 얼마나 지워졌는지. */
export interface DataResetReport {
  clearedTables: string[];
  clearedRows: number;
}

/**
 * 데이터 초기화 (#285). **최고 관리자만.**
 *
 * 확인 문구를 서버도 다시 확인한다 — 화면의 입력만으로는 잘못 눌린 요청을 막지 못한다.
 * 기능이 꺼져 있으면 404 다(켜져 있는지 여부까지 감춘다).
 */
export const adminDataApi = {
  reset: (confirmation: string) =>
    request<DataResetReport>("/api/v1/admin/data/reset", {
      method: "POST",
      auth: true,
      body: { confirmation },
    }),
};

export const DATA_RESET_CONFIRMATION = "문제와 제출을 모두 지웁니다";
