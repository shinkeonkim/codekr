import type { PostSummary } from "@/entities/post";
import { formatDate } from "@/shared/lib";
import { Card } from "@/shared/ui";
import Link from "next/link";

/**
 * 첫 화면의 공지 (#263, #275).
 *
 * **없으면 아예 보이지 않는다.** 처음 온 사람에게 "공지사항 없음" 이라고 적힌 빈 상자를
 * 보이는 것은, 이 사이트가 비어 있다고 말하는 것과 같다. 그 판단은 두 단을 함께 보는
 * `HomePage` 가 한다 — 여기서 `null` 을 돌려주면 옆 칸이 반쪽으로 남는다.
 *
 * 목록을 다시 만들지 않고 게시판의 공지 게시판을 그대로 읽는다 — 공지를 두 곳에 쓰게
 * 되면 언젠가 한쪽만 갱신된다.
 */
export function NoticeBoard({ notices }: { notices: PostSummary[] }) {
  return (
    <section className="space-y-4">
      <div className="flex items-baseline justify-between">
        <h2 className="text-lg font-semibold text-ink">공지사항</h2>
        <Link href="/posts?board=NOTICE" className="text-sm text-brand hover:underline">
          전체 보기 →
        </Link>
      </div>

      <Card className="divide-y divide-border">
        {notices.map((notice) => (
          <Link
            key={notice.id}
            href={`/posts/${notice.id}`}
            className="flex items-center gap-3 px-5 py-3.5 transition hover:bg-surface-muted/40"
          >
            <span className="min-w-0 flex-1 truncate text-sm font-medium text-ink">
              {notice.title}
            </span>
            {/* 댓글 수는 공지에서 거의 뜻이 없다. 언제 올라왔는지만 남긴다. */}
            <span className="shrink-0 text-xs text-ink-muted">{formatDate(notice.createdAt)}</span>
          </Link>
        ))}
      </Card>
    </section>
  );
}
