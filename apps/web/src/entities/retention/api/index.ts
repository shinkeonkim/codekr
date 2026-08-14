import { request } from "@/shared/api";
import type { RetentionReport } from "../model/types";

export const retentionApi = {
  /** 소프트 삭제된 행을 실제로 지운다 (#46). 새벽 4시에 자동으로도 돈다. */
  cleanup: () => request<RetentionReport>("/api/v1/admin/retention/cleanup", { method: "POST", auth: true }),
};
