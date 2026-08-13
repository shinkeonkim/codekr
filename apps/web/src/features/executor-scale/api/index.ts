import { request } from "@/shared/api";
import type { ExecutorScaleStatus } from "../model/types";

/**
 * 채점 파이프라인 워크로드 조정 (#40, #390).
 *
 * **경로가 `executors` 가 아니라 `workloads` 다.** 조정할 수 있는 것이 실행기 하나뿐이
 * 아니게 됐다 — 채점기가 큐를 못 빼면 실행기를 늘려도 소용없다.
 */
export const executorScaleApi = {
  /** 조정할 수 있는 것 전부. **화면이 목록을 들고 있지 않는다** — 대상이 늘어도 안 고친다. */
  statuses: () => request<ExecutorScaleStatus[]>("/api/v1/admin/workloads", { auth: true }),

  /** 파드 수 — 처리량과 격리에 듣는다. */
  scale: (key: string, replicas: number) =>
    request<ExecutorScaleStatus>(`/api/v1/admin/workloads/${key}/scale`, {
      method: "POST",
      body: { replicas },
      auth: true,
    }),

  /** 워커 수 — 대기 시간에 듣는다. **재시작하지 않는다** (#390). */
  setWorkers: (key: string, workers: number) =>
    request<ExecutorScaleStatus>(`/api/v1/admin/workloads/${key}/workers`, {
      method: "POST",
      body: { workers },
      auth: true,
    }),
};
