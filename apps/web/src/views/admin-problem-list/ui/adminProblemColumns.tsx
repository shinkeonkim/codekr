import { CATEGORY_LABELS, TierBadge } from "@/entities/problem";
import type { ProblemCategory, ProblemSummary } from "@/entities/problem";
import { Badge } from "@/shared/ui";
import type { Column } from "@/shared/ui";
import { AdminProblemActions } from "./AdminProblemActions";

/**
 * 어드민 문제 목록의 열 (#625).
 *
 * **사용자 목록(`PROBLEM_COLUMNS`)과 열이 다르다.** 저쪽은 "무엇을 풀까" 를 고르므로
 * 정답률·제한이 필요하지만, 여기서 묻는 것은 **"이 문제가 지금 어떤 상태인가"** 다.
 * 그래서 공개 여부가 열로 서 있고, 실행 제한은 아예 없다.
 *
 * 좁은 화면에 남는 것은 **번호·문제·공개 여부·작업**이다. 넷째부터는 줄이 접힌다는
 * #193 의 규칙을 따르되, 작업은 감출 수 없다 — 감추면 그 화면에서 할 수 있는 일이 없다.
 */
export function adminProblemColumns(onRemove: (id: number) => void): Column<ProblemSummary>[] {
  return [
    {
      key: "id",
      header: "번호",
      // 카드 목록에는 번호가 아예 없었다. 로그도 API 도 번호로 말하는데 화면에는 slug 뿐이라,
      // 어드민이 번호를 알려면 편집 화면을 열어 주소를 읽어야 했다.
      width: "w-20",
      render: (problem) => <span className="tabular-nums text-ink-muted">{problem.id}</span>,
    },
    {
      key: "title",
      header: "문제",
      // **고르는 동작은 제목에 건다** (#379). 어드민에서 문제를 고르는 뜻은 "고친다" 이다.
      href: (problem) => `/admin/problems/${problem.id}/edit`,
      render: (problem) => (
        <span className="block">
          <span className="block truncate">{problem.title}</span>
          <span className="mt-0.5 block truncate text-xs font-normal text-ink-muted">{problem.slug}</span>
        </span>
      ),
    },
    {
      key: "category",
      header: "유형",
      hideBelow: "lg",
      render: (problem) => (
        <span className="text-ink-muted">{CATEGORY_LABELS[problem.category as ProblemCategory]}</span>
      ),
    },
    {
      key: "difficulty",
      header: "난이도",
      hideBelow: "sm",
      align: "center",
      render: (problem) => <TierBadge difficulty={problem.difficulty} label={problem.difficultyLabel} />,
    },
    {
      key: "published",
      header: "공개",
      align: "center",
      /*
        **이 열이 이 화면의 이유다.** 카드일 때는 제목 길이에 따라 뱃지의 가로 위치가
        줄마다 달라서, 미공개가 몇 개인지 훑어서 셀 수 없었다. 열로 서면 세로로 읽힌다.
      */
      render: (problem) => (
        <Badge tone={problem.published ? "ok" : "muted"}>{problem.published ? "공개" : "미공개"}</Badge>
      ),
    },
    {
      key: "actions",
      header: "작업",
      align: "right",
      render: (problem) => <AdminProblemActions problem={problem} onRemove={onRemove} />,
    },
  ];
}
