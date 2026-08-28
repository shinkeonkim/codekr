import { describe, expect, test } from "bun:test";
import { parseGitGraph } from "./gitGraph";

/**
 * 지문의 커밋 그래프 (#720).
 *
 * **읽을 수 없으면 `null`** 이어야 한다. 그때 부르는 쪽이 코드 블록을 그대로 그리므로
 * 지문이 사라지지 않는다 — 그럴듯한데 틀린 그림보다 낫다.
 */
describe("지문의 커밋 그래프", () => {
  test("겹쳐 쓴 앞부분이 곧 분기점이다", () => {
    const graph = parseGitGraph(["main:  A B C", "feat:  A B D"])!;

    expect(graph.branches).toEqual(["main", "feat"]);
    // A·B 는 한 번만 그린다. 겹쳐 썼다고 커밋이 늘지 않는다.
    expect(graph.commits.map((each) => each.label)).toEqual(["A", "B", "C", "D"]);
    expect(graph.commits.find((each) => each.label === "D")).toMatchObject({ column: 2, row: 1 });
  });

  test("갈래가 나는 선만 굽는다", () => {
    const graph = parseGitGraph(["main:  A B C", "feat:  A B D"])!;

    // B→D 는 줄을 갈아탄다. 나머지는 같은 줄에서 곧게 간다.
    expect(graph.edges.filter((each) => each.curved)).toEqual([{ from: "B", to: "D", curved: true }]);
  });

  test("merge 는 두 번째 부모를 잇는다", () => {
    const graph = parseGitGraph(["main:  A B M", "feat:  A C", "merge: C M"])!;

    expect(graph.edges).toContainEqual({ from: "C", to: "M", curved: true });
  });

  test("head 와 tag 를 읽는다", () => {
    const graph = parseGitGraph(["main: A B", "head: main", "tag: B v1.0"])!;

    expect(graph.head).toBe("main");
    expect(graph.commits.find((each) => each.label === "B")?.tag).toBe("v1.0");
  });

  test("같은 커밋이 다른 칸에 오면 못 읽었다고 답한다", () => {
    // **그림과 글이 다른 말을 하게 된다.** A 가 어디서 갈라졌는지가 가지마다 달라진다.
    expect(parseGitGraph(["main: A B C", "feat: X A"])).toBeNull();
  });

  test("없는 커밋을 가리키는 head·merge·tag 는 못 읽었다고 답한다", () => {
    expect(parseGitGraph(["main: A B", "head: feat"])).toBeNull();
    expect(parseGitGraph(["main: A B", "merge: A Z"])).toBeNull();
    expect(parseGitGraph(["main: A B", "tag: Z v1"])).toBeNull();
  });

  test("이 문법이 아닌 것은 못 읽었다고 답한다", () => {
    // 사용자가 ```gitgraph 를 쓰고 아무거나 적을 수 있다.
    expect(parseGitGraph(["그냥 글입니다"])).toBeNull();
    expect(parseGitGraph(["main:"])).toBeNull();
    expect(parseGitGraph([])).toBeNull();
  });

  test("같은 가지를 두 번 적으면 못 읽었다고 답한다", () => {
    // 뒤엣것이 앞엣것을 조용히 덮으면, 지운 줄이 왜 사라졌는지 알 수 없다.
    expect(parseGitGraph(["main: A B", "main: A C"])).toBeNull();
  });

  test("주석과 빈 줄은 건너뛴다", () => {
    expect(parseGitGraph(["# 설명", "", "main: A B"])!.commits).toHaveLength(2);
  });
});

describe("가지의 끝", () => {
  test("갈래를 내지 않은 가지도 무엇을 가리키는지 안다", () => {
    // main 이 앞서 있고 topic 은 B 에 머물러 있다. **topic 의 줄에는 커밋이 없다** —
    // 줄에서 유추하면 topic 이 C 를 가리킨다고 잘못 읽는다.
    const graph = parseGitGraph(["main: A B C", "topic: A B"])!;

    expect(graph.tips).toEqual({ main: "C", topic: "B" });
  });

  test("두 가지가 같은 커밋을 가리킬 수 있다", () => {
    // `git branch hotfix` 를 막 부른 직후의 모습이다.
    const graph = parseGitGraph(["main: A", "hotfix: A"])!;

    expect(graph.tips).toEqual({ main: "A", hotfix: "A" });
  });
});
