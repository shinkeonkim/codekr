/**
 * SQL 실행 결과를 표로 읽는다 (#525).
 *
 * 하네스가 실행일 때 CSV 로 내놓는다 — 열 이름이 첫 줄에 오고, 값에 든 쉼표·따옴표·
 * 줄바꿈은 따옴표로 감싸인다. **그 규칙을 제대로 풀어야 한다**: 값 안의 쉼표에서
 * 칸을 자르면 표가 통째로 어긋난다.
 *
 * NULL 은 하네스가 `∅` 로 적는다. CSV 에는 NULL 이 없어서, 그러지 않으면 "값이 없다"
 * 와 "빈 글자" 가 화면에서 같아진다.
 */

/** 하네스가 NULL 자리에 적는 값. `run-sql.sh` 와 같아야 한다. */
export const SQL_NULL = "∅";

export interface SqlResultTable {
  columns: string[];
  /** `null` 은 SQL 의 NULL 이다. 빈 문자열과 다르다. */
  rows: (string | null)[][];
}

/**
 * CSV 한 덩어리를 셀로 자른다 (RFC 4180).
 *
 * **따옴표 안에서는 쉼표도 줄바꿈도 글자다.** 그래서 줄 단위로 먼저 자를 수 없다 —
 * 한 글자씩 읽으며 상태를 들고 간다.
 */
function splitCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = "";
  let quoted = false;
  let touched = false;

  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];

    if (quoted) {
      if (char !== '"') {
        cell += char;
      } else if (text[index + 1] === '"') {
        // 따옴표 안의 따옴표는 두 번 적는다.
        cell += '"';
        index += 1;
      } else {
        quoted = false;
      }
      continue;
    }

    if (char === '"') {
      quoted = true;
      touched = true;
    } else if (char === ",") {
      row.push(cell);
      cell = "";
      touched = true;
    } else if (char === "\n" || char === "\r") {
      if (char === "\r" && text[index + 1] === "\n") index += 1;
      if (touched || cell.length > 0) {
        row.push(cell);
        rows.push(row);
      }
      row = [];
      cell = "";
      touched = false;
    } else {
      cell += char;
      touched = true;
    }
  }

  if (touched || cell.length > 0) {
    row.push(cell);
    rows.push(row);
  }
  return rows;
}

/**
 * 실행 결과를 표로 읽는다. 표가 아니면 `null` — 그때는 지금까지처럼 글자 그대로 보인다.
 *
 * **표가 아닌 결과가 많다.** `INSERT`·`UPDATE`(#453)는 결과 집합이 없고, 오류 메시지는
 * stderr 로 온다. 억지로 표로 만들면 없는 구조를 지어내는 것이다.
 */
export function parseSqlResult(stdout: string): SqlResultTable | null {
  const text = stdout.trimEnd();
  if (text.length === 0) return null;

  const cells = splitCsv(text);
  if (cells.length === 0) return null;

  const [header, ...body] = cells;
  // 열이 하나뿐이면 표로 볼 근거가 약하다 — 그냥 글자 덩어리일 수 있다.
  if (header.length < 2 && body.length === 0) return null;

  return {
    columns: header,
    // **칸 수가 모자란 줄은 채우고, 넘치는 줄은 자르지 않는다.** 표가 어긋난 것을
    // 감추면 무엇이 잘못됐는지 알 수 없다.
    rows: body.map((cells) => cells.map((value) => (value === SQL_NULL ? null : value))),
  };
}
