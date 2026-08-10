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
