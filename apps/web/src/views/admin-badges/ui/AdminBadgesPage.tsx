"use client";

import { badgeApi } from "@/entities/badge";
import type { BadgeDefinition, BadgeRule } from "@/entities/badge";
import { ApiError } from "@/shared/api";
import { Badge, Button, Card, EmptyState, useToast } from "@/shared/ui";
import { useEffect, useState } from "react";
import { RuleDryRun } from "./RuleDryRun";

/**
 * 뱃지 관리 (#201, #203).
 *
 * 정의(문구·노출)와 규칙(조건)이 한 화면에 있다 — 둘은 `rule_key` 로 이어져 있어서
 * 따로 두면 "이 뱃지가 왜 안 나오지" 를 볼 때 화면을 오간다.
 */
export function AdminBadgesPage() {
  const toast = useToast();
  const [definitions, setDefinitions] = useState<BadgeDefinition[]>([]);
  const [rules, setRules] = useState<BadgeRule[]>([]);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    badgeApi
      .adminAll()
      .then(setDefinitions)
      .catch(() => setDefinitions([]));
    badgeApi
      .rules()
      .then(setRules)
      .catch(() => setRules([]));
  }, [reloadKey]);

  const toggleVisible = async (definition: BadgeDefinition) => {
    try {
      await badgeApi.updateDefinition(definition.code, {
        label: definition.label,
        description: definition.description,
        visible: !definition.visible,
        sortOrder: definition.sortOrder,
      });
      // **문구를 고치면 이미 받은 사람에게도 바뀐다** (#201) — 숨김도 즉시 반영된다.
      toast.success(definition.visible ? "숨겼습니다." : "다시 보이게 했습니다.");
      setReloadKey((key) => key + 1);
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "바꾸지 못했습니다.");
    }
  };

  const toggleRule = async (rule: BadgeRule) => {
    try {
      await badgeApi.setRuleEnabled(rule.ruleKey, !rule.enabled);
      // **지우는 것보다 끄는 것이 먼저다** (#203).
      toast.success(rule.enabled ? "규칙을 껐습니다." : "규칙을 켰습니다.");
      setReloadKey((key) => key + 1);
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "바꾸지 못했습니다.");
    }
  };

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold text-ink">뱃지</h1>
        <p className="mt-1 text-sm text-ink-muted">
          문구와 노출은 배포 없이 바뀝니다. 규칙은 저장하기 전에 결과를 볼 수 있습니다.
        </p>
      </div>

      <RuleDryRun />

      <section className="space-y-2">
        <h2 className="text-sm font-semibold text-ink">규칙</h2>
        {rules.length === 0 ? <EmptyState title="규칙이 없습니다." /> : null}
        {rules.map((rule) => (
          <Card key={rule.ruleKey} className="flex flex-wrap items-center gap-3 p-4 text-sm">
            <span className="font-medium text-ink">{rule.ruleKey}</span>
            <Badge tone="muted">{rule.event}</Badge>
            <span className="flex-1 truncate text-xs text-ink-muted">
              {rule.conditions.length === 0
                ? "조건 없음 (이벤트가 곧 달성)"
                : rule.conditions
                    .map((condition) => `${condition.measure} ${condition.op} ${condition.value}`)
                    .join(" · ")}
            </span>
            <Badge tone={rule.enabled ? "ok" : "muted"}>{rule.enabled ? "켜짐" : "꺼짐"}</Badge>
            <Button variant="ghost" className="px-2 py-0.5 text-xs" onClick={() => toggleRule(rule)}>
              {rule.enabled ? "끄기" : "켜기"}
            </Button>
          </Card>
        ))}
      </section>

      <section className="space-y-2">
        <h2 className="text-sm font-semibold text-ink">정의</h2>
        {definitions.map((definition) => (
          <Card key={definition.code} className="flex flex-wrap items-center gap-3 p-4 text-sm">
            <span className="font-medium text-ink">{definition.label}</span>
            <span className="text-xs text-ink-muted">{definition.code}</span>
            <span className="flex-1 truncate text-xs text-ink-muted">{definition.description}</span>
            {/* 규칙과 어떻게 이어지는지 보여야 "왜 안 나오지" 를 여기서 답할 수 있다. */}
            <Badge tone="muted">{definition.ruleKey}</Badge>
            <Button
              variant="ghost"
              className="px-2 py-0.5 text-xs"
              onClick={() => toggleVisible(definition)}
            >
              {definition.visible ? "숨기기" : "보이기"}
            </Button>
          </Card>
        ))}
      </section>
    </div>
  );
}
