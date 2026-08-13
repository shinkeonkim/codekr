/**
 * 로그인하면 안 되는 화면의 판정 (#311).
 *
 * **판정을 그리기 전에 끝낸다.** 로그인 화면의 옛 가드는 `useEffect` 라 한 번 그려진
 * 뒤에 돌아서, 로그인한 사람에게 폼이 잠깐 번쩍였다 (#206 이 테마에서 겪은 것과 같다).
 */
export type GuestGate = "wait" | "redirect" | "allow";

export function guestGate(loading: boolean, signedIn: boolean): GuestGate {
  // 아직 모르는 상태에서 폼을 그리면, 로그인한 사람에게 한 번 번쩍인다.
  if (loading) return "wait";
  return signedIn ? "redirect" : "allow";
}
