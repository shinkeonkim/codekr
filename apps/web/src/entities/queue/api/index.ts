import { request } from "@/shared/api";
import type { QueueStatus } from "../model/types";

export const queueApi = {
  status: () => request<QueueStatus>("/api/v1/admin/queues", { auth: true }),
};
