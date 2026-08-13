/** 조정할 수 있는 워크로드 하나의 상태 (#40, #390). */
export interface ExecutorScaleStatus {
  /** 조정할 때 경로에 쓰는 이름. 설정의 허용 목록 키다. */
  key: string;
  /** 사람이 읽는 이름 — 실행기·채점기·대회 채점기. */
  label: string;
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
  /**
   * 지금 정해진 워커 수 (#390). 채점기에만 있다.
   *
   * **null 은 "정한 적이 없다" 다** — 그때 채점기는 기동값을 쓴다. 0 이 아니다.
   */
  workers: number | null;
  reason: string | null;
  /** 조정 버튼을 보일지. 읽기 실패와 별개다 — 권한이 scale 에만 있어도 조정은 된다. */
  controllable: boolean;
}
