/** 실행기 배포의 replica 상태 (#40). */
export interface ExecutorScaleStatus {
  /**
   * 세 가지인 이유 (#237): "클러스터 밖이라 못 한다" 와 "읽기에 실패했다" 는 다른 일이다.
   * 앞은 설정이고 뒤는 고장이라, 화면이 다르게 말해야 한다.
   */
  state: "OUTSIDE_CLUSTER" | "UNREADABLE" | "OK";
  deployment: string;
  namespace: string | null;
  desiredReplicas: number;
  readyReplicas: number;
  minReplicas: number;
  maxReplicas: number;
  reason: string | null;
  /** 조정 버튼을 보일지. 읽기 실패와 별개다 — 권한이 scale 에만 있어도 조정은 된다. */
  controllable: boolean;
}
