/** 채점·실행 큐의 관측값 (어드민 모니터링). */

export interface StreamStatus {
  name: string;
  group: string;
  length: number;
  pending: number;
  consumers: number;
  lastDeliveredId: string | null;
  ready: boolean;
}

export interface QueueStatus {
  streams: StreamStatus[];
}
