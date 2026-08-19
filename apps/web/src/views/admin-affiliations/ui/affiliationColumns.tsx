import type { Affiliation } from "@/entities/affiliation";
import { Badge, Button, ConfirmDialog } from "@/shared/ui";
import type { Column } from "@/shared/ui";

/**
 * 소속 목록의 열 (#633).
 *
 * **"도메인 없음" 이 열로 선다.** 도메인이 없는 소속은 아무에게도 붙지 않는데(#428),
 * 카드일 때는 그 경고가 카드 안쪽 문장이라 하나씩 열어 봐야 했다. 이 화면에서 가장
 * 자주 묻는 것이 그 질문이다.
 */
export function affiliationColumns(
  openId: number | null,
  onToggleOpen: (id: number) => void,
  onRemove: (affiliation: Affiliation) => void,
): Column<Affiliation>[] {
  return [
    {
      key: "name",
      header: "이름",
      render: (affiliation) => <span className="font-medium text-ink">{affiliation.name}</span>,
    },
    {
      key: "kind",
      header: "종류",
      hideBelow: "sm",
      render: (affiliation) => <Badge tone="muted">{affiliation.kindLabel}</Badge>,
    },
    {
      key: "domains",
      header: "도메인",
      align: "center",
      render: (affiliation) =>
        affiliation.domains.length === 0 ? (
          <span className="whitespace-nowrap text-xs text-danger">없음</span>
        ) : (
          <span className="tabular-nums text-xs text-ink-muted">{affiliation.domains.length}개</span>
        ),
    },
    {
      key: "manage",
      header: "도메인 관리",
      align: "center",
      render: (affiliation) => (
        <Button
          variant="ghost"
          className="whitespace-nowrap px-2 py-0.5 text-xs"
          onClick={() => onToggleOpen(affiliation.id)}
        >
          {openId === affiliation.id ? "접기" : "펼치기"}
        </Button>
      ),
    },
    {
      key: "actions",
      header: "작업",
      align: "right",
      render: (affiliation) => (
        <ConfirmDialog
          trigger={
            <Button variant="ghost" className="whitespace-nowrap px-2 py-0.5 text-xs">
              내리기
            </Button>
          }
          title={`'${affiliation.name}' 을 내립니다`}
          description="도메인이 함께 떨어져 새로 붙는 사람이 없어집니다. 이미 붙인 사람에게는 그대로 남습니다."
          confirmLabel="내리기"
          onConfirm={() => onRemove(affiliation)}
        />
      ),
    },
  ];
}
