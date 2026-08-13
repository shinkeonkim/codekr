import type { Comment } from "@/entities/post";

/**
 * 이미 그린 트리에 새로 받은 것을 **겹치지 않게** 잇는다 (#213).
 *
 * 같은 댓글이 두 번 그려지는 것이 트리에서 가장 나쁜 결과다. 커서로 이어받으면 서버가
 * 겹치지 않게 주지만, **쓰기·삭제 응답은 앞쪽부터 다시 온다** — 그때는 이미 펼쳐 둔
 * 것을 잃지 않으면서 바뀐 것만 갈아 끼워야 한다.
 *
 * 규칙:
 * - 같은 id 가 양쪽에 있으면 **새 것의 내용**을 쓰되, 자식은 양쪽을 합친다
 *   (펼쳐 둔 것이 도로 접히지 않는다)
 * - 한쪽에만 있으면 그대로 둔다
 * - 순서는 id 순 — 서버가 주는 순서와 같다
 */
export function mergeComments(existing: Comment[], incoming: Comment[]): Comment[] {
  const byId = new Map<number, Comment>();
  for (const comment of existing) byId.set(comment.id, comment);

  for (const fresh of incoming) {
    const old = byId.get(fresh.id);
    byId.set(fresh.id, old ? mergeOne(old, fresh) : fresh);
  }

  return [...byId.values()].sort((a, b) => a.id - b.id);
}

function mergeOne(old: Comment, fresh: Comment): Comment {
  return {
    ...fresh,
    children: mergeComments(old.children, fresh.children),
    /*
      남은 개수는 **더 적은 쪽**을 쓴다.

      펼쳐 둔 자식이 새 응답에 안 들어 있으면 서버가 말하는 "남은 수" 는 그것까지 세고
      있다. 이미 화면에 있는 것을 또 세면 "3개 더" 를 눌러도 아무것도 안 늘어난다.
    */
    remainingChildren: Math.max(0, Math.min(old.remainingChildren, fresh.remainingChildren)),
  };
}

/** 이어받을 커서 — 이 자리에서 마지막으로 받은 댓글 id. */
export function lastId(comments: Comment[]): number | undefined {
  return comments.length > 0 ? comments[comments.length - 1].id : undefined;
}
