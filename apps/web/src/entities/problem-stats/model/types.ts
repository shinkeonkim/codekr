/**
 * 저장된 통계와 실제가 어긋난 문제 (#205, #551).
 *
 * **저장하기로 한 이상 어긋남을 볼 경로가 있어야 한다** — 랭킹(#177)에서 이미 정한
 * 규칙이다. 값이 어긋났을 때 되돌릴 방법이 없으면 애초에 저장하면 안 된다.
 */
export interface StatsDrift {
  problemId: number;
  storedSubmitters: number;
  storedSolvers: number;
  actualSubmitters: number;
  actualSolvers: number;
}
