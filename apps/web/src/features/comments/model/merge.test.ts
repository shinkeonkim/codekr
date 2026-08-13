import { describe, expect, it } from "bun:test";
import type { Comment } from "@/entities/post";
import { lastId, mergeComments } from "./merge";

function comment(id: number, children: Comment[] = [], remainingChildren = 0): Comment {
  return {
    id,
    parentId: null,
    authorNickname: "누군가",
    authorAvatarUrl: null,
    body: `댓글 ${id}`,
    deleted: false,
    createdAt: "2026-01-01T00:00:00Z",
    editedAt: null,
    edited: false,
    editable: false,
    deletable: false,
    children,
    remainingChildren,
    mentions: [],
  };
}

describe("mergeComments", () => {
  it("같은 댓글이 두 번 그려지지 않는다", () => {
    // 트리에서 가장 나쁜 결과다 (#213).
    const merged = mergeComments([comment(1), comment(2)], [comment(2), comment(3)]);
    expect(merged.map((each) => each.id)).toEqual([1, 2, 3]);
  });

  it("펼쳐 둔 자식이 도로 접히지 않는다", () => {
    // 쓰기·삭제 응답은 앞쪽부터 다시 온다 — 그때 이미 편 것을 잃으면 안 된다.
    const opened = comment(1, [comment(10), comment(11)]);
    const fromServer = comment(1, [comment(10)], 1);

    const merged = mergeComments([opened], [fromServer]);
    expect(merged[0].children.map((each) => each.id)).toEqual([10, 11]);
  });

  it("이미 화면에 있는 것을 남은 수에 또 세지 않는다", () => {
    // 세면 "1개 더" 를 눌러도 아무것도 안 늘어난다.
    const opened = comment(1, [comment(10), comment(11)], 0);
    const fromServer = comment(1, [comment(10)], 1);

    expect(mergeComments([opened], [fromServer])[0].remainingChildren).toBe(0);
  });

  it("새 내용이 이긴다", () => {
    const old = comment(1);
    const edited = { ...comment(1), body: "고친 내용", edited: true };

    expect(mergeComments([old], [edited])[0].body).toBe("고친 내용");
  });
});

describe("lastId", () => {
  it("마지막으로 받은 id 가 커서다", () => {
    expect(lastId([comment(1), comment(5)])).toBe(5);
    expect(lastId([])).toBeUndefined();
  });
});
