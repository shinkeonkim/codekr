import type { BadgeDefinition, BadgeRule } from "@/entities/badge";
import { Badge, Button } from "@/shared/ui";
import type { Column } from "@/shared/ui";

/**
 * 뱃지 규칙·정의 목록의 열 (#632).
 *
 * **상태를 값으로 세운다.** 카드일 때는 켜짐/꺼짐이 제목 길이를 따라 줄마다 다른
 * 자리에 떠서 "지금 꺼진 규칙이 무엇인가" 를 훑어서 셀 수 없었다. 게다가 버튼 글자
 * (`끄기`·`보이기`)는 **지금 상태가 아니라 누르면 될 상태**라, 상태를 버튼으로 읽으면
 * 매번 한 번 뒤집어야 한다. 그래서 상태 열과 작업 열을 나눈다.
 */
export function ruleColumns(onToggle: (rule: BadgeRule) => void): Column<BadgeRule>[] {
  return [
    {
      key: "ruleKey",
      header: "규칙 키",
      render: (rule) => <span className="font-medium text-ink">{rule.ruleKey}</span>,
    },
    {
      key: "event",
      header: "이벤트",
      hideBelow: "sm",
      render: (rule) => <Badge tone="muted">{rule.event}</Badge>,
    },
    {
      key: "conditions",
      header: "조건",
      hideBelow: "lg",
      render: (rule) => (
        <span className="text-xs text-ink-muted">
          {rule.conditions.length === 0
            ? "조건 없음 (이벤트가 곧 달성)"
            : rule.conditions
                .map((condition) => `${condition.measure} ${condition.op} ${condition.value}`)
                .join(" · ")}
        </span>
      ),
    },
    {
      key: "enabled",
      header: "상태",
      align: "center",
      render: (rule) => <Badge tone={rule.enabled ? "ok" : "muted"}>{rule.enabled ? "켜짐" : "꺼짐"}</Badge>,
    },
    {
      key: "actions",
      header: "작업",
      align: "right",
      render: (rule) => (
        // **지우는 것보다 끄는 것이 먼저다** (#203).
        <Button variant="ghost" className="px-2 py-0.5 text-xs" onClick={() => onToggle(rule)}>
          {rule.enabled ? "끄기" : "켜기"}
        </Button>
      ),
    },
  ];
}

export function definitionColumns(
  onToggle: (definition: BadgeDefinition) => void,
): Column<BadgeDefinition>[] {
  return [
    {
      key: "label",
      header: "이름",
      render: (definition) => <span className="font-medium text-ink">{definition.label}</span>,
    },
    {
      key: "code",
      header: "코드",
      hideBelow: "sm",
      render: (definition) => <span className="text-xs text-ink-muted">{definition.code}</span>,
    },
    {
      key: "description",
      header: "설명",
      hideBelow: "lg",
      render: (definition) => <span className="text-xs text-ink-muted">{definition.description}</span>,
    },
    {
      key: "ruleKey",
      header: "규칙 키",
      hideBelow: "lg",
      // 규칙과 어떻게 이어지는지 보여야 "왜 안 나오지" 를 여기서 답할 수 있다.
      render: (definition) => <Badge tone="muted">{definition.ruleKey}</Badge>,
    },
    {
      key: "visible",
      header: "노출",
      align: "center",
      render: (definition) => (
        <Badge tone={definition.visible ? "ok" : "muted"}>{definition.visible ? "보임" : "숨김"}</Badge>
      ),
    },
    {
      key: "actions",
      header: "작업",
      align: "right",
      render: (definition) => (
        <Button variant="ghost" className="px-2 py-0.5 text-xs" onClick={() => onToggle(definition)}>
          {definition.visible ? "숨기기" : "보이기"}
        </Button>
      ),
    },
  ];
}
