import { CATEGORY_LABELS, TierBadge } from "@/entities/problem";
import type { ProblemCategory, ProblemSummary } from "@/entities/problem";
import { Badge, Checkbox } from "@/shared/ui";
import type { Column } from "@/shared/ui";
import { AdminProblemActions } from "./AdminProblemActions";

/** 고르기 (#627). 고르는 기능이 없는 화면에서는 통째로 빠진다. */
export interface Selection {
  ids: Set<number>;
  onToggle: (id: number) => void;
  /** 지금 장을 통째로 고르거나 푼다. **다음 장까지 고르지 않는다** — 보이지 않는 것을 바꾸게 된다. */
  onToggleAll: () => void;
  /** 지금 장에 있는 문제들. 머리글 체크박스의 상태는 **이 장을 기준으로** 정해진다. */
  pageIds: number[];
}

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
export function adminProblemColumns(
  onRemove: (id: number) => void,
  selection?: Selection,
): Column<ProblemSummary>[] {
  const select: Column<ProblemSummary>[] = selection
    ? [
        {
          key: "select",
          width: "w-10",
          /*
            **머리글의 체크박스는 "이 장 전부" 다** (#627). 조건에 맞는 전부가 아니다.
            보이지 않는 것까지 바꾸면 되돌리기 어려운 실수의 크기에 한계가 없어진다 —
            "미공개 300건" 을 한 번에 공개하고 싶다면 그것은 따로 물어야 하는 일이다.
          */
          header: (
            <Checkbox
              aria-label="이 장 전부 고르기"
              checked={headerState(selection)}
              onCheckedChange={selection.onToggleAll}
            />
          ),
          render: (problem) => (
            <Checkbox
              aria-label={`${problem.title} 고르기`}
              checked={selection.ids.has(problem.id)}
              onCheckedChange={() => selection.onToggle(problem.id)}
            />
          ),
        },
      ]
    : [];

  return [
    ...select,
    {
      key: "id",
      header: "번호",
      // 카드 목록에는 번호가 아예 없었다. 로그도 API 도 번호로 말하는데 화면에는 slug 뿐이라,
      // 어드민이 번호를 알려면 편집 화면을 열어 주소를 읽어야 했다.
      //
      // **좁은 화면에서는 접는다** — 고르는 칸(#627)이 붙으면서 열이 다섯이 됐고,
      // 560px 에서 표가 담는 칸보다 37px 넓어져 **삭제 버튼이 잘렸다.** 감싼 상자가
      // `overflow-hidden` 이라 잘린 부분에는 닿을 수도 없다.
      hideBelow: "sm",
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
        <Badge tone={problem.published ? "ok" : "muted"}>
          {/* 좁은 화면에서 "공/개" 로 접혀 두 줄이 됐다. */}
          <span className="whitespace-nowrap">{problem.published ? "공개" : "미공개"}</span>
        </Badge>
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

/**
 * 머리글 체크박스의 상태.
 *
 * **지금 장을 기준으로 센다.** 고른 개수만 보면, 1장에서 스무 개를 고르고 2장으로
 * 넘어갔을 때 아무것도 안 골랐는데 "전부 골라짐" 으로 보인다.
 */
export function headerState(selection: Selection): boolean | "indeterminate" {
  const picked = selection.pageIds.filter((id) => selection.ids.has(id)).length;
  if (picked === 0) return false;
  return picked === selection.pageIds.length ? true : "indeterminate";
}
