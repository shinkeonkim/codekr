/**
 * 확인 문자열을 세는 규칙 (#653).
 *
 * **화면에서 떼어 둔다.** 여기서 세는 숫자가 출제자에게 "이 문제가 무엇을 묻는가" 를
 * 알려 준다 — 맞으면 안 되는 줄이 0이면 `.*` 가 통과하는 문제이고, 그것은 오류를
 * 내지 않아 **저장한 뒤에는 알 방법이 없다.**
 *
 * 서버도 같은 규칙으로 막지만(`ProblemUpsertValidator`), 저장을 눌러 보고 아는 것과
 * 쓰면서 아는 것은 다르다.
 */
export interface CaseCounts {
  positive: number;
  negative: number;
  /** `+`·`-` 로 시작하지 않는 줄. 서버가 거부한다. */
  malformed: number;
}

export function countCases(cases: string): CaseCounts {
  const counts: CaseCounts = { positive: 0, negative: 0, malformed: 0 };
  for (const raw of cases.split("\n")) {
    // 빈 줄은 자료가 아니다 — 파일 끝의 개행 하나로 줄이 하나 늘면 안 된다.
    const line = raw.trim();
    if (line === "") continue;
    if (line.startsWith("+")) counts.positive += 1;
    else if (line.startsWith("-")) counts.negative += 1;
    else counts.malformed += 1;
  }
  return counts;
}
