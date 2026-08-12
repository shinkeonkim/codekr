/** 도메인을 모르는 표시용 변환들. */

export function formatMemory(kilobytes: number): string {
  if (kilobytes <= 0) return "-";
  if (kilobytes < 1024) return `${kilobytes} KB`;
  return `${(kilobytes / 1024).toFixed(1)} MB`;
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/**
 * 날짜만 (#263).
 *
 * 공지처럼 **하루 단위로 읽는 것**에 시:분을 붙이면 눈이 걸린다. 분 단위가 뜻을 갖는
 * 자리(제출 시각)에는 formatDateTime 을 쓴다.
 */
export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
}
