import type { GitGraph as Graph, GitGraphCommit } from "./gitGraph";

/**
 * 커밋 그래프를 그린다 (#720).
 *
 * **HTML 문자열을 만들지 않는다.** 지문에 SVG 를 그대로 허용하면 `<script>` 와
 * `<foreignObject>` 가 함께 들어오고, 그 구멍은 사용자가 쓴 것을 그리는 모든
 * 자리(댓글·질문)로 이어진다 (#137). 여기서는 **읽어 낸 데이터로 React 엘리먼트를
 * 직접 만든다** — 사용자가 쓴 글자는 텍스트 노드로만 들어간다.
 *
 * 색은 전부 CSS 변수를 쓴다. 라이트·다크가 `light-dark()` 로 한 자리에서 정해지므로
 * 여기서 두 벌을 관리하지 않는다.
 */

const MIN_COLUMN = 62;
const ROW = 58;
/** 첫 커밋의 이름표가 왼쪽으로 잘리지 않을 만큼. 이름은 가운데 맞춤이라 반쯤 넘친다. */
const LEFT = 44;
const TOP = 26;
const RADIUS = 9;
/** 이름표에 내주는 자리. 글자 폭은 재지 않고 넉넉히 잡는다 — 한글이 가장 넓다. */
const LABEL_EM = 11;

/** 가지마다 다른 색. 넘치면 처음부터 다시 쓴다 — 안 쓰는 것보다 낫다. */
const LANE_COLORS = [
  "var(--color-brand)",
  "var(--color-tier-gold)",
  "var(--color-tier-platinum)",
  "var(--color-tier-ruby)",
  "var(--color-ok)",
  "var(--color-warn)",
];

function at(commit: GitGraphCommit, column: number) {
  return { x: LEFT + commit.column * column, y: TOP + commit.row * ROW };
}

/**
 * 칸 사이를 얼마나 벌릴 것인가.
 *
 * **고정하면 한글 이름이 옆 칸을 침범한다.** 커밋 이름은 출제자가 정하고 `첫커밋`·
 * `main작업` 처럼 한글이 섞인다 — 라틴 글자 기준으로 잡은 폭으로는 모자란다.
 */
function columnWidth(graph: Graph) {
  const longest = Math.max(...graph.commits.map((commit) => commit.label.length));
  return Math.max(MIN_COLUMN, longest * LABEL_EM + 12);
}

/**
 * 줄을 갈아타는 선.
 *
 * 곧은 선으로 이으면 대각선이 되어 어느 가지에 붙는지가 흐려진다. **떠난 자리에서
 * 곧게 나와 도착한 줄로 눕는** 모양이라야 "여기서 갈라졌다" 가 읽힌다.
 */
function curve(from: { x: number; y: number }, to: { x: number; y: number }) {
  const middle = from.x + (to.x - from.x) / 2;
  return `M ${from.x} ${from.y} C ${middle} ${from.y}, ${middle} ${to.y}, ${to.x} ${to.y}`;
}

/**
 * 가지 이름을 **끝 커밋 옆에** 붙인다.
 *
 * 줄 왼쪽에 세로로 늘어놓으면, 갈라지지 않은 가지의 줄이 **텅 빈 채로 이름만
 * 남는다** — 실제로 `git branch` 로 막 만든 가지가 그렇다. 그리고 그것은 git 이
 * 스스로 보여 주는 모양도 아니다: `(HEAD -> main, hotfix)` 처럼 **가리키는 커밋에
 * 붙여** 쓴다. 그러면 두 가지가 같은 커밋을 가리키는 것도 그대로 그려진다.
 */
function tipLabels(graph: Graph) {
  const byTip = new Map<string, { text: string; lane: number }[]>();
  for (const [lane, branch] of graph.branches.entries()) {
    const tip = graph.tips[branch];
    const text = graph.head === branch ? `HEAD → ${branch}` : branch;
    // **색은 커밋의 줄이 아니라 그 가지의 것이다.** 갈래를 내지 않은 가지는 남의
    // 줄에 얹혀 있어서, 커밋 색을 쓰면 두 가지가 같은 가지처럼 보인다.
    byTip.set(tip, [...(byTip.get(tip) ?? []), { text, lane }]);
  }
  return byTip;
}

export function GitGraphView({ graph }: { graph: Graph }) {
  const column = columnWidth(graph);
  const positions = new Map(graph.commits.map((commit) => [commit.label, at(commit, column)]));
  const rows = new Map(graph.commits.map((commit) => [commit.label, commit.row]));
  const columns = Math.max(...graph.commits.map((commit) => commit.column)) + 1;
  const labels = tipLabels(graph);
  const widest = Math.max(...[...labels.values()].map((names) => Math.max(...names.map((each) => each.text.length))));
  const width = LEFT + columns * column + widest * LABEL_EM;
  // 한 커밋에 이름표가 여럿 쌓이면 그만큼 아래가 더 필요하다.
  const stacks = Math.max(...[...labels.values()].map((names) => names.length));
  const height = TOP + graph.branches.length * ROW + (stacks - 1) * 15;

  return (
    <div className="overflow-x-auto rounded-lg border border-border bg-surface-muted p-3">
      <svg
        width={width}
        height={height}
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label={`커밋 그래프: ${graph.branches.join(", ")}`}
        className="max-w-none"
      >
        {graph.edges.map((edge, index) => {
          const from = positions.get(edge.from)!;
          const to = positions.get(edge.to)!;
          const color = LANE_COLORS[(rows.get(edge.to) ?? 0) % LANE_COLORS.length];
          return edge.curved ? (
            <path key={index} d={curve(from, to)} fill="none" stroke={color} strokeWidth={2} />
          ) : (
            <line key={index} x1={from.x} y1={from.y} x2={to.x} y2={to.y} stroke={color} strokeWidth={2} />
          );
        })}

        {graph.commits.map((commit) => {
          const names = labels.get(commit.label);
          if (!names) return null;
          const point = at(commit, column);
          return names.map((name, stacked) => (
            <text
              key={`${commit.label}-${name.text}`}
              x={point.x + RADIUS + 8}
              /* 같은 커밋을 가리키는 이름이 둘이면 겹치지 않게 쌓는다. */
              y={point.y + 4 + stacked * 15}
              fontSize={12}
              fill={LANE_COLORS[name.lane % LANE_COLORS.length]}
              /* HEAD 가 어디를 가리키는지가 문제의 절반인 경우가 많다 (#654). */
              fontWeight={name.text.startsWith("HEAD") ? 700 : 400}
            >
              {name.text}
            </text>
          ));
        })}

        {graph.commits.map((commit) => {
          const point = at(commit, column);
          const color = LANE_COLORS[commit.row % LANE_COLORS.length];
          return (
            <g key={commit.label}>
              <circle cx={point.x} cy={point.y} r={RADIUS} fill="var(--color-surface)" stroke={color} strokeWidth={2} />
              {/*
                이름은 **동그라미 안이 아니라 위에** 쓴다.

                안에 넣으면 두 글자를 넘는 순간 잘라야 하고, 잘린 이름은 `merge:`·
                `tag:` 가 가리키는 이름과 달라 보인다. 아래에 두면 **오른쪽에 쌓이는
                가지 이름과 부딪힌다** — 두 가지가 같은 커밋을 가리킬 때 실제로 겹쳤다.
              */}
              <text x={point.x} y={point.y - RADIUS - 6} textAnchor="middle" fontSize={11} fill="var(--color-ink)">
                {commit.label}
              </text>
              {commit.tag ? (
                <text x={point.x} y={point.y + RADIUS + 14} textAnchor="middle" fontSize={10} fill="var(--color-ink-muted)">
                  {commit.tag}
                </text>
              ) : null}
            </g>
          );
        })}
      </svg>
    </div>
  );
}
