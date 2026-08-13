import type { ReactNode } from "react";
import { CodeBlock } from "./CodeBlock";

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
 * 지원: 문단, 코드 블록(```, 언어에 맞게 칠한다 #384), 인라인 코드, **굵게**,
 * 링크, **이미지**(우리 저장소의 것만, #389), 불릿 목록, 제목(#).
 *
 * **기울임(`*x*`·`_x_`)은 넣지 않았다** (#338). 문제 지문에는 `loans.member_id` 처럼
 * 밑줄이 든 식별자가 흔하다 — 기울임을 켜면 그 이름들이 조용히 기울어진다.
 * 시드의 SQL 문제 다섯이 전부 그렇다. 얻는 것보다 잃는 것이 크다.
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
      // 언어에 맞게 칠한다 (#384). **모르는 언어는 그대로 둔다** — `CodeBlock` 이 정한다.
      const code = <CodeBlock code={body.join("\n")} language={language || undefined} />;
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
  const pattern = /(`[^`]+`)|(\*\*(?=\S)([^*]+)\*\*)|(!\[([^\]]*)\]\(([^)\s]+)\))|(\[([^\]]+)\]\(([^)\s]+)\))|@\{u:(\d+)}/g;
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
    } else if (match[2]) {
      /*
        **굵게** (#338).

        시드 문제 `a-divided-by-b` 가 이미 `**정답은 실수입니다.**` 라고 쓰고 있었다 —
        출제자는 마크다운이라고 여기고 썼는데 별표가 그대로 보였다.
      */
      nodes.push(
        <strong key={key++} className="font-semibold text-ink">
          {match[3]}
        </strong>,
      );
    } else if (match[4]) {
      /*
        이미지 (#389).

        **우리 저장소의 것만 그린다.** 남의 주소를 그리면 추적 픽셀과 혼합 콘텐츠가
        들어오고, 그 링크는 언젠가 깨진다 — `isSafeUrl` 이 링크에 대해 한 것과 같은
        판단이다(#137). 아닌 것은 **링크로** 남긴다: 주소를 지우면 쓴 사람이 무엇을
        가리켰는지조차 알 수 없다.
      */
      const src = match[6];
      nodes.push(
        isOwnAttachment(src) ? (
          /*
            폭을 넘기지 않고 원래 비율을 지킨다. 높이를 박으면 세로 그림이 뭉개진다.

            **`next/image` 를 쓰지 않는다.** 그것은 크기를 미리 알아야 하는데, 남이
            올린 그림의 크기는 저장하지 않는다 — 마크다운 본문 안이라 `fill` 을 쓸
            부모 상자도 없다. 서버가 이미 1600px·JPEG 로 다시 만들어 두므로(#115)
            최적화가 할 일도 크지 않다.
          */
          // eslint-disable-next-line @next/next/no-img-element
          <img
            key={key++}
            src={src}
            alt={match[5]}
            className="my-2 h-auto max-w-full rounded-lg border border-border"
            loading="lazy"
          />
        ) : (
          <a key={key++} href={isSafeUrl(src) ? src : undefined} className="text-brand underline">
            {match[5] || src}
          </a>
        ),
      );
    } else if (match[10]) {
      /*
        멘션 (#214).

        **색만으로 구분하지 않는다** — 이 저장소의 규칙이다(`ToastViewport`·잔디 그래프).
        `@` 를 남겨 두는 것이 색 외의 단서다.

        이름표가 없으면 링크로 만들지 않는다. 탈퇴했거나 지워진 계정이라 갈 곳이 없다.
      */
      const nickname = labels.get(Number(match[10]));
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
      const href = match[9];
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
            {match[8]}
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
/**
 * 우리가 올린 첨부인가 (#389).
 *
 * **남의 주소는 그리지 않는다.** 그리면 추적 픽셀이 들어오고, 그 링크는 언젠가 깨진다.
 * 아바타(`/files/avatars/`)도 아니다 — 본문에 남의 아바타를 박을 이유가 없다.
 */
export function isOwnAttachment(url: string): boolean {
  return url.startsWith("/api/v1/files/attachments/");
}

export function isSafeUrl(href: string): boolean {
  if (href.startsWith("/")) return !href.startsWith("//");
  return /^https?:\/\//i.test(href);
}
