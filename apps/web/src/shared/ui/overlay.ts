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
  toastViewport:
    "pointer-events-none fixed inset-x-4 bottom-4 z-toast flex flex-col items-stretch gap-2 sm:inset-x-auto sm:right-4 sm:items-end",
  /** 토스트 하나의 최대 폭. 좁은 화면에서는 부모가 이미 가로를 채운다. */
  toastItem: "sm:w-96",
} as const;
