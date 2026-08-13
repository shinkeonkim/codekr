import type { ReactNode } from "react";

/**
 * 최소 마크다운 렌더러 (#137).
 *
 * **HTML 문자열을 만들지 않는다.** `dangerouslySetInnerHTML` 을 쓰지 않고 React 엘리먼트로
 * 직접 만들기 때문에, 사용자가 쓴 `<script>` 는 **문자열로 그려질 뿐 실행되지 않는다.**
 *
 * 마크다운 라이브러리를 들이지 않은 이유: 그 경우 sanitize 설정을 정확히 맞춰야 하고,
 * 설정 하나가 어긋나면 구멍이 난다. 사용자가 쓴 것을 그리는 첫 기능이라 **구멍이 날 수
 * 없는 방식**을 골랐다. 여기서 새는 구멍은 나중에 붙는 댓글·질문에 그대로 이어진다.
 *
 * 지원: 문단, 코드 블록(```), 인라인 코드, 링크, 불릿 목록, 제목(#).
 */
export function Markdown({
  source,
  hideCode = false,
  /**
   * 멘션 이름표 (#214). `@{u:42}` 를 무엇으로 그릴지 알려 준다.
   *
   * **없으면 표기를 그대로 두지 않고 "알 수 없는 사용자" 로 그린다** — 저장 표기가
   * 그대로 보이면 사용자는 그것이 무엇인지 알 수 없다.
   */
  mentions,
}: {
  source: string;
  hideCode?: boolean;
  mentions?: { id: number; nickname: string }[];
}) {
  const labels = new Map((mentions ?? []).map((each) => [each.id, each.nickname]));
  return (
    <div className="space-y-3 text-sm leading-relaxed text-ink">
      {renderBlocks(source, hideCode, labels)}
    </div>
  );
}

function renderBlocks(source: string, hideCode: boolean, labels: Map<number, string>): ReactNode[] {
  const blocks: ReactNode[] = [];
  const lines = source.replace(/\r\n/g, "\n").split("\n");

  let index = 0;
  let key = 0;
  while (index < lines.length) {
    const line = lines[index];

    if (line.startsWith("```")) {
      const language = line.slice(3).trim();
      const body: string[] = [];
      index += 1;
      while (index < lines.length && !lines[index].startsWith("```")) {
        body.push(lines[index]);
        index += 1;
      }
      index += 1; // 닫는 ```
      const code = (
        <pre
          className="overflow-x-auto rounded-lg bg-surface-muted p-3 text-xs"
          data-language={language || undefined}
        >
          <code>{body.join("\n")}</code>
        </pre>
      );
      blocks.push(
        hideCode ? (
          // 문제 질문에는 정답 코드가 그대로 올라온다 (#139). 기본으로 접고 펼칠 수 있게 한다.
          <details key={key++} className="rounded-lg border border-border p-2">
            <summary className="cursor-pointer text-xs text-ink-muted">
              코드 보기 — 아직 풀지 않았다면 답이 보일 수 있습니다
            </summary>
            <div className="mt-2">{code}</div>
          </details>
        ) : (
          <div key={key++}>{code}</div>
        ),
      );
      continue;
    }

    const heading = /^(#{1,3})\s+(.*)$/.exec(line);
    if (heading) {
      const level = heading[1].length;
      const sizes = ["text-lg font-bold", "text-base font-bold", "text-sm font-semibold"];
      blocks.push(
        <p key={key++} className={`${sizes[level - 1]} text-ink`}>
          {renderInline(heading[2], labels)}
        </p>,
      );
      index += 1;
      continue;
    }

    if (/^[-*]\s+/.test(line)) {
      const items: string[] = [];
      while (index < lines.length && /^[-*]\s+/.test(lines[index])) {
        items.push(lines[index].replace(/^[-*]\s+/, ""));
        index += 1;
      }
      blocks.push(
        <ul key={key++} className="list-disc space-y-1 pl-5">
          {items.map((item, i) => (
            <li key={i}>{renderInline(item, labels)}</li>
          ))}
        </ul>,
      );
      continue;
    }

    if (line.trim() === "") {
      index += 1;
      continue;
    }

    const paragraph: string[] = [];
    while (index < lines.length && lines[index].trim() !== "" && !lines[index].startsWith("```")) {
      paragraph.push(lines[index]);
      index += 1;
    }
    blocks.push(
      <p key={key++} className="whitespace-pre-wrap break-words">
        {renderInline(paragraph.join("\n"), labels)}
      </p>,
    );
  }

  return blocks;
}

/**
 * 인라인 코드와 링크와 멘션. 나머지는 글자 그대로 둔다.
 *
 * **코드 블록 안의 표기는 멘션이 되지 않는다** (#214). 코드 블록은 위에서 통째로
 * 처리되고 여기까지 오지 않으며, 인라인 코드는 이 패턴의 첫 갈래가 먼저 잡는다.
 */
function renderInline(text: string, labels: Map<number, string>): ReactNode[] {
  const nodes: ReactNode[] = [];
  const pattern = /(`[^`]+`)|(\[([^\]]+)\]\(([^)\s]+)\))|@\{u:(\d+)}/g;
  let last = 0;
  let key = 0;
  let match: RegExpExecArray | null;

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) nodes.push(text.slice(last, match.index));

    if (match[1]) {
      nodes.push(
        <code key={key++} className="rounded bg-surface-muted px-1 py-0.5 text-xs">
          {match[1].slice(1, -1)}
        </code>,
      );
    } else if (match[5]) {
      /*
        멘션 (#214).

        **색만으로 구분하지 않는다** — 이 저장소의 규칙이다(`ToastViewport`·잔디 그래프).
        `@` 를 남겨 두는 것이 색 외의 단서다.

        이름표가 없으면 링크로 만들지 않는다. 탈퇴했거나 지워진 계정이라 갈 곳이 없다.
      */
      const nickname = labels.get(Number(match[5]));
      nodes.push(
        nickname ? (
          <a
            key={key++}
            href={`/users/${encodeURIComponent(nickname)}`}
            className="rounded bg-brand/10 px-1 font-medium text-brand hover:underline"
          >
            @{nickname}
          </a>
        ) : (
          <span key={key++} className="rounded bg-surface-muted px-1 text-ink-muted">
            @알 수 없는 사용자
          </span>
        ),
      );
    } else {
      const href = match[4];
      // **javascript: 같은 주소는 링크로 만들지 않는다.** 글자 그대로 둔다.
      nodes.push(
        isSafeUrl(href) ? (
          <a
            key={key++}
            href={href}
            className="text-brand underline"
            target="_blank"
            // 새 창으로 열 때 opener 를 넘기지 않는다.
            rel="noreferrer noopener"
          >
            {match[3]}
          </a>
        ) : (
          <span key={key++}>{match[0]}</span>
        ),
      );
    }
    last = pattern.lastIndex;
  }
  if (last < text.length) nodes.push(text.slice(last));
  return nodes;
}

/** http/https 와 사이트 안의 경로만 링크로 만든다. */
export function isSafeUrl(href: string): boolean {
  if (href.startsWith("/")) return !href.startsWith("//");
  return /^https?:\/\//i.test(href);
}
