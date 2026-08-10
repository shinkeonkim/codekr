/** 실행기 배포의 replica 상태 (#40). */
export interface ExecutorScaleStatus {
  available: boolean;
  deployment: string;
  desiredReplicas: number;
  readyReplicas: number;
  minReplicas: number;
  maxReplicas: number;
  reason: string | null;
}
