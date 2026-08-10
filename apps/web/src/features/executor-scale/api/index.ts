import { request } from "@/shared/api";
import type { ExecutorScaleStatus } from "../model/types";

export const executorScaleApi = {
  status: () => request<ExecutorScaleStatus>("/api/v1/admin/executors", { auth: true }),

  scale: (replicas: number) =>
    request<ExecutorScaleStatus>("/api/v1/admin/executors/scale", {
      method: "POST",
      body: { replicas },
      auth: true,
    }),
};
