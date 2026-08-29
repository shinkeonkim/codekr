/**
 * 지문의 커밋 그래프를 읽는다 (#720).
 *
 * **가지 관계는 글로 그리기 어렵다.** 작업 트리 상태는 `git status --short` 를 지문에
 * 붙이면 되지만, "main 에 커밋 셋, feature 가 두 번째에서 갈라져 커밋 하나" 는 읽고
 * 머릿속에 그리게 하는 일이 된다 — 그건 문제의 본질이 아니다.
 *
 * 파싱을 따로 둔 이유는 표(#590)와 같다: `Markdown.tsx` 를 더 키우지 않고, **그래프가
 * 제대로 읽히는지를 브라우저 없이 확인**할 수 있게.
 *
 * ## 문법
 *
 *     main:  A B C
 *     feat:  A B D E
 *     head:  feat
 *     merge: E C
 *     tag:   C v1.0
 *
 * **같은 이름은 같은 커밋이다.** 그래서 갈래가 어디서 났는지를 따로 안 적는다 —
 * 앞부분을 겹쳐 쓰면 그것이 곧 분기점이다. mermaid 의 `gitGraph` 를 따르지 않은
 * 이유는 그쪽이 명령형(`commit`/`branch`/`checkout`)이라 같은 그림에 줄이 훨씬
 * 많이 들고, 부분만 지원하면 **되는 문법과 안 되는 문법의 경계가 안 보이기** 때문이다.
 */

export interface GitGraphCommit {
  label: string;
  /** 몇 번째 칸인가. 겹쳐 쓴 커밋은 모든 가지에서 같은 칸에 온다. */
  column: number;
  /** 어느 가지의 줄에 그릴 것인가. 겹친 커밋은 **처음 나온 가지**의 줄에 산다. */
  row: number;
  tag?: string;
}

export interface GitGraphEdge {
  from: string;
  to: string;
  /** 갈래가 나거나 합쳐지는 자리인가. 곧게 잇는 선과 다르게 그린다. */
  curved: boolean;
}

export interface GitGraph {
  branches: string[];
  commits: GitGraphCommit[];
  edges: GitGraphEdge[];
  /**
   * 가지마다 그 끝 커밋의 이름.
   *
   * **줄에서 유추하면 안 된다.** 갈래를 내지 않은 가지는 자기 줄에 커밋이 하나도
   * 없어서, 그때 무엇을 가리키는지는 적힌 차례에만 남아 있다.
   */
  tips: Record<string, string>;
  head?: string;
}

const RESERVED = new Set(["head", "merge", "tag"]);
/** 한 그림이 감당할 크기. 넘으면 그리지 않고 코드 블록 그대로 둔다. */
const MAX_BRANCHES = 8;
const MAX_COLUMNS = 16;

/** `키: 값` 한 줄. 값이 없으면 이 문법이 아니다. */
function splitDirective(line: string): [string, string] | null {
  const at = line.indexOf(":");
  if (at <= 0) return null;
  const key = line.slice(0, at).trim();
  const value = line.slice(at + 1).trim();
  if (!key || !value || /\s/.test(key)) return null;
  return [key, value];
}

/**
 * ` ```gitgraph ` 블록의 본문을 읽는다.
 *
 * **읽을 수 없으면 `null` 이다.** 부르는 쪽은 그때 코드 블록을 그대로 그린다 —
 * 지문이 사라지는 것보다 문법이 그대로 보이는 편이 낫다.
 */
export function parseGitGraph(body: string[]): GitGraph | null {
  const branches: string[] = [];
  const sequences = new Map<string, string[]>();
  const tags = new Map<string, string>();
  const merges: [string, string][] = [];
  let head: string | undefined;

  for (const raw of body) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;

    const directive = splitDirective(line);
    if (!directive) return null;
    const [key, value] = directive;
    const words = value.split(/\s+/);

    if (key === "head") {
      if (words.length !== 1) return null;
      head = words[0];
    } else if (key === "merge") {
      // `merge: <어디서> <어디로>` — 두 번째 부모를 잇는 선 하나.
      if (words.length !== 2) return null;
      merges.push([words[0], words[1]]);
    } else if (key === "tag") {
      // `tag: <커밋> <붙일 글자>` — 남는 말이 곧 이름표다.
      if (words.length < 2) return null;
      tags.set(words[0], words.slice(1).join(" "));
    } else {
      if (sequences.has(key)) return null;
      branches.push(key);
      sequences.set(key, words);
    }
  }

  if (!branches.length) return null;
  if (branches.length > MAX_BRANCHES) return null;
  if (head !== undefined && !sequences.has(head)) return null;

  const commits: GitGraphCommit[] = [];
  const placed = new Map<string, GitGraphCommit>();
  const edges: GitGraphEdge[] = [];
  let broken = false;

  for (const [row, branch] of branches.entries()) {
    const sequence = sequences.get(branch)!;
    if (sequence.length > MAX_COLUMNS) return null;

    let previous: GitGraphCommit | undefined;
    for (const [column, label] of sequence.entries()) {
      let commit = placed.get(label);
      if (commit) {
        // **겹쳐 쓴 커밋은 같은 칸에 와야 한다.** 어긋나면 갈래가 어디서 났는지를
        // 그림과 글이 다르게 말하게 된다. **고쳐 그리지 않고 못 읽었다고 답한다** —
        // 그럴듯한 그림이 틀린 것보다 문법이 그대로 보이는 편이 낫다.
        if (commit.column !== column) broken = true;
      } else {
        commit = { label, column, row };
        placed.set(label, commit);
        commits.push(commit);
      }
      if (previous) {
        edges.push({ from: previous.label, to: commit.label, curved: previous.row !== commit.row });
      }
      previous = commit;
    }
  }
  if (broken) return null;

  for (const [from, to] of merges) {
    if (!placed.has(from) || !placed.has(to)) return null;
    edges.push({ from, to, curved: true });
  }
  for (const [label, text] of tags) {
    const commit = placed.get(label);
    if (!commit) return null;
    commit.tag = text;
  }

  const tips: Record<string, string> = {};
  for (const branch of branches) {
    const sequence = sequences.get(branch)!;
    tips[branch] = sequence[sequence.length - 1];
  }

  return { branches, commits, edges, tips, head };
}
