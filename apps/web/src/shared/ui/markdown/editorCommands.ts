/**
 * 편집기 버튼이 글자에 무엇을 하는가 (#388).
 *
 * **화면에서 떼어 낸 이유는 시험 때문이다.** 커서 위치와 선택 범위를 다루는 코드는
 * 눈으로 읽어서는 맞는지 알 수 없고, 화면에서 확인하려면 매번 눌러 봐야 한다.
 */
export interface EditResult {
  text: string;
  /** 적용한 뒤 커서(또는 선택)가 있어야 할 자리. 없으면 사용자가 다시 찾아야 한다. */
  selectionStart: number;
  selectionEnd: number;
}

/**
 * 고른 글자를 앞뒤로 감싼다 — 굵게, 인라인 코드.
 *
 * **아무것도 안 골랐으면 표시만 넣고 그 사이에 커서를 둔다.** 빈 `****` 를 남기고
 * 커서를 끝에 두면 사용자가 화살표로 되돌아와야 한다.
 */
export function wrap(text: string, start: number, end: number, mark: string): EditResult {
  const selected = text.slice(start, end);
  const next = `${text.slice(0, start)}${mark}${selected}${mark}${text.slice(end)}`;
  return {
    text: next,
    selectionStart: start + mark.length,
    selectionEnd: start + mark.length + selected.length,
  };
}

/**
 * 고른 줄 앞에 표시를 붙인다 — 목록, 인용.
 *
 * **줄 단위다.** 선택이 줄 중간에서 시작해도 그 줄 처음부터 붙인다 — 목록 표시가
 * 줄 가운데 들어가면 마크다운이 아니라 글자가 된다.
 */
export function linePrefix(text: string, start: number, end: number, prefix: string): EditResult {
  const lineStart = text.lastIndexOf("\n", start - 1) + 1;
  /*
    **선택이 줄 첫머리에서 끝나면 그 줄은 넣지 않는다.**

    세 줄 중 둘을 고르려고 셋째 줄 첫 글자 앞까지 끌면, 사람이 고른 것은 두 줄이다.
    편집기들이 그렇게 다루고, 그렇게 하지 않으면 **고르지 않은 줄이 목록이 된다.**
  */
  const scanFrom = end > start && text[end - 1] === "\n" ? end - 1 : end;
  const lineEnd = text.indexOf("\n", scanFrom) === -1 ? text.length : text.indexOf("\n", scanFrom);
  const block = text.slice(lineStart, lineEnd);
  const prefixed = block
    .split("\n")
    .map((line) => `${prefix}${line}`)
    .join("\n");

  return {
    text: `${text.slice(0, lineStart)}${prefixed}${text.slice(lineEnd)}`,
    selectionStart: lineStart,
    selectionEnd: lineStart + prefixed.length,
  };
}

/**
 * 코드 블록으로 감싼다.
 *
 * **이 버튼이 이 편집기의 이유다.** "이 코드가 왜 틀렸나요" 가 질문 게시판의
 * 대부분인데(#139), 코드 블록을 못 감싸면 들여쓰기가 뭉개지고 답하는 사람이 코드를
 * 읽을 수 없다.
 *
 * 앞뒤로 빈 줄을 둔다 — 붙어 있으면 앞 문단과 한 덩어리로 읽혀 블록이 되지 않는다.
 */
export function codeFence(text: string, start: number, end: number, language = ""): EditResult {
  const selected = text.slice(start, end);
  const before = text.slice(0, start);
  const after = text.slice(end);
  const lead = before === "" || before.endsWith("\n\n") ? "" : before.endsWith("\n") ? "\n" : "\n\n";
  const tail = after === "" || after.startsWith("\n") ? "" : "\n";

  const opening = `${lead}\`\`\`${language}\n`;
  const next = `${before}${opening}${selected}\n\`\`\`\n${tail}${after}`;
  return {
    text: next,
    // 고른 것이 없으면 블록 안에 커서를 둔다 — 바로 코드를 붙여 넣을 수 있게.
    selectionStart: before.length + opening.length,
    selectionEnd: before.length + opening.length + selected.length,
  };
}
