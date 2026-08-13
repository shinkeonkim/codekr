/**
 * 컴파일 오류가 **어느 파일의 몇 번 줄**인가 (#457, #498).
 *
 * 파일이 하나일 때는 줄 번호만 있으면 됐다. 여럿이면 `Helper.java:17` 이 어느 탭의
 * 이야기인지 사용자가 눈으로 찾아야 하는데, **오류 메시지의 모양이 언어마다 다르다.**
 *
 * ```
 * Helper.java:17: error: ';' expected          자바
 *   File "helper.py", line 3                   파이썬
 * helper.cpp:5:1: error: expected ';'          C·C++
 * ./helper.go:4:6: syntax error                Go
 * ```
 *
 * **모양을 다 아는 척하지 않는다.** 대신 우리가 아는 것 — 이 문제의 파일 이름 —을
 * 문자열에서 찾고, 그 뒤에 붙은 첫 숫자를 줄 번호로 본다. 언어가 늘어도 이 규칙은 남고,
 * 못 찾으면 **아무 말도 하지 않는다** — 틀린 자리를 가리키는 것이 침묵보다 나쁘다.
 */
export interface ErrorLocation {
  file: string;
  line: number | null;
}

export function findErrorLocation(
  message: string,
  fileNames: string[],
): ErrorLocation | null {
  let best: { location: ErrorLocation; at: number } | null = null;

  for (const name of fileNames) {
    const at = message.indexOf(name);
    if (at < 0) continue;
    // 이름 바로 뒤에서 첫 숫자를 찾는다. `helper.py", line 3` 처럼 사이에 글자가
    // 끼어도 같은 줄 안이면 그것이 줄 번호다.
    const rest = message.slice(at + name.length, at + name.length + 40);
    const line = rest.match(/(\d+)/)?.[1];
    const found = {
      location: { file: name, line: line ? Number(line) : null },
      at,
    };
    // **가장 먼저 나온 것**을 고른다. 컴파일러는 대개 첫 오류를 먼저 말한다.
    if (!best || found.at < best.at) best = found;
  }

  return best?.location ?? null;
}
