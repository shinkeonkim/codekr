/**
 * 이 언어에서 무엇으로 시작할 것인가 (#383).
 *
 * **화면에서 떼어 낸 이유는 시험 때문이다.** 제출한 코드가 자기가 쓴 것이 아니었다는
 * 사고가 이 판단에서 났다 — 그런데 그 판단이 렌더 안에 있으면 확인할 방법이 화면을
 * 눌러 보는 것뿐이고, 그것은 "언제 눌렀는가" 에 따라 결과가 달라진다.
 */
export const draftKey = (slug: string, runtimeId: string) => `codekr.draft.${slug}.${runtimeId}`;

/** 서버 렌더링 중에는 저장소가 없으므로 초안이 없는 것으로 본다. */
export function readDraft(slug: string, runtimeId: string): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(draftKey(slug, runtimeId));
}

/**
 * 초안이 있으면 초안, 없으면 템플릿.
 *
 * **빈 문자열도 초안이다.** 전에는 빈 값을 저장하지 않아서, 코드를 전부 지우고 나가면
 * 다시 들어왔을 때 **옛 초안이 되살아났다.** 지운 것도 사용자가 한 일이다.
 */
export function initialSource(draft: string | null, template: string): string {
  return draft ?? template;
}
