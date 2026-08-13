/**
 * 화면에 고정되는 것들의 자리와 층 (#134).
 *
 * **한 곳에서 관리한다.** 우측 하단에 다른 고정 요소(도움말 버튼 등)가 생겼을 때,
 * 각자 자기 위치를 들고 있으면 서로 겹친 뒤에야 알게 된다.
 */
export const OVERLAY = {
  /**
   * 토스트가 뜨는 자리.
   *
   * 데스크톱은 **우측 하단** — "지나가는 알림"의 관례적인 자리라 학습 비용이 없고,
   * 본문 중앙을 비워 둔다. 결과를 알리면서 원래 내용도 계속 보여야 한다.
   *
   * 좁은 화면에서는 좌우 여백만 두고 가로로 채운다. 우측에 붙이면 글이 서너 줄로 접힌다.
   */
  toastPosition: "bottom-right",
  /**
   * 한 번에 쌓아 둘 최대 개수. 넘치면 오래된 것부터 밀어낸다.
   *
   * 넷 이상 쌓이면 화면 오른쪽이 통째로 가려진다 — 알리려던 것을 알리지 못하게 된다.
   */
  toastMaxVisible: 3,
  /** 자동으로 사라지기까지의 시간(ms). 닫기 버튼이 있으므로 너무 길게 두지 않는다. */
  toastDurationMs: 5_000,
  /** 토스트 하나의 최대 폭. 좁은 화면에서는 아래 `toastItem` 이 가로를 채운다. */
  toastWidth: "24rem",
  /** 가장자리에서 띄우는 거리. 좁은 화면은 좌우 여백만 두고 가로로 채운다. */
  toastOffset: "1rem",
  toastItem:
    "pointer-events-auto flex w-full items-start gap-3 rounded-lg border px-4 py-3 text-sm shadow-lg backdrop-blur",
  toastTone: {
    success: "border-ok/40 bg-ok/12 text-ok",
    error: "border-danger/40 bg-danger/12 text-danger",
    info: "border-info/40 bg-info/12 text-info",
  },
} as const;
