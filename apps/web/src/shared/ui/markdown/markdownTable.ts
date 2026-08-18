/**
 * 지문의 표를 읽는다 (#590).
 *
 * **표가 파이프 그대로 보였다.** 최소 렌더러(#137)에 표 규칙이 없어서 `|---|---|` 같은
 * 구분선까지 글자로 나왔다. 시드 45개 중 13개가 표를 쓰고, **SQL 문제는 표를 읽어야
 * 쿼리를 쓸 수 있다** — 예시 데이터가 표다 (#526).
 *
 * 파싱을 따로 둔 이유: `Markdown.tsx` 는 이미 260줄이고, **표가 제대로 읽히는지는
 * 브라우저 없이 확인할 수 있어야 한다.**
 */

export interface MarkdownTable {
  header: string[];
  rows: string[][];
  /** 표가 끝난 다음 줄. 부르는 쪽이 여기서부터 계속 읽는다. */
  next: number;
}

/** 구분선인가 — `|---|:--:|` 처럼 대시와 콜론만 있는 줄. */
function isDivider(line: string): boolean {
  return /^\s*\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)*\|?\s*$/.test(line) && line.includes("-");
}

/**
 * 한 줄을 칸으로 나눈다.
 *
 * **`\|` 는 값이다.** 값에 파이프가 든 경우가 실제로 있다 (#532 가 채점에서 겪는 그것) —
 * 이스케이프를 못 읽으면 칸이 하나 더 생겨 표가 어긋난다.
 */
export function splitCells(line: string): string[] {
  const cells: string[] = [];
  let cell = "";
  for (let index = 0; index < line.length; index += 1) {
    const char = line[index];
    if (char === "\\" && line[index + 1] === "|") {
      cell += "|";
      index += 1;
    } else if (char === "|") {
      cells.push(cell);
      cell = "";
    } else {
      cell += char;
    }
  }
  cells.push(cell);

  // 줄 앞뒤의 `|` 가 만든 빈 칸은 칸이 아니다. `| a | b |` 는 두 칸이다.
  if (cells.length > 0 && cells[0].trim() === "") cells.shift();
  if (cells.length > 0 && cells[cells.length - 1].trim() === "") cells.pop();
  return cells.map((it) => it.trim());
}

/**
 * [start] 에서 표를 읽는다. 표가 아니면 `null`.
 *
 * **머리글 다음 줄이 구분선이어야 한다.** 그러지 않으면 `| 를 쓴 평범한 문단`이
 * 표가 되어 버린다 — 지문에는 `a | b` 같은 글이 그냥 나올 수 있다.
 */
export function parseTable(lines: string[], start: number): MarkdownTable | null {
  const first = lines[start];
  if (first === undefined || !first.trim().startsWith("|")) return null;
  if (!isDivider(lines[start + 1] ?? "")) return null;

  const header = splitCells(first);
  const rows: string[][] = [];
  let index = start + 2;
  while (index < lines.length && lines[index].trim().startsWith("|")) {
    const cells = splitCells(lines[index]);
    /*
      **칸 수가 모자라면 채우고, 넘치면 그대로 둔다.** 어긋난 표를 감추면 무엇이
      잘못됐는지 알 수 없고, 그렇다고 줄을 통째로 버리면 내용이 사라진다.
    */
    while (cells.length < header.length) cells.push("");
    rows.push(cells);
    index += 1;
  }

  return { header, rows, next: index };
}
