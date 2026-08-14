"use client";

import { badgeApi } from "@/entities/badge";
import type { BadgeDefinition, BadgeDryRun, BadgeRule, BadgeVocabulary } from "@/entities/badge";
import { ApiError } from "@/shared/api";
import { Button, Card, CardTitle, Field, Input, Select, useToast } from "@/shared/ui";
import { useEffect, useState } from "react";
import { DryRunResult, RuleList } from "./RuleEditorParts";

/**
 * 규칙을 만들고 고친다 (#203, #549).
 *
 * ## 저장 버튼이 없는 편집기였다
 *
 * #203 이 **미리보기**를 넣었고 그건 제대로 들어갔는데, 저장할 대상이 없었다.
 * 규칙을 짜서 몇 명이 받을지 볼 수는 있어도 **본 것을 남길 수 없었다.**
 *
 * 그래서 #200 이 규칙 엔진을 만든 이유("코드를 고치지 않고 뱃지를 늘린다")가 반쯤
 * 죽어 있었다 — 넣을 문이 화면에 없으니 DB 를 직접 만지거나 시드를 고쳐 배포해야 했다.
 *
 * ## 미리보기를 강제하지 않는다
 *
 * 오타 하나 고치는 데도 지나야 하면 번거롭다. 대신 **저장 버튼 옆에 늘 미리보기를 둔다** —
 * 누르는 것이 한 번이면 습관이 된다.
 */
export function RuleEditor({
  rules,
  badges,
  onSaved,
}: {
  rules: BadgeRule[];
  badges: BadgeDefinition[];
  onSaved: () => void;
}) {
  const toast = useToast();
  const [vocabulary, setVocabulary] = useState<BadgeVocabulary | null>(null);
  const [ruleKey, setRuleKey] = useState("");
  const [code, setCode] = useState("");
  const [event, setEvent] = useState("PROBLEM_ACCEPTED");
  const [measure, setMeasure] = useState("accepted_problem_count");
  const [op, setOp] = useState(">=");
  const [value, setValue] = useState("10");
  const [userId, setUserId] = useState("");
  const [result, setResult] = useState<BadgeDryRun | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    badgeApi
      .vocabulary()
      .then(setVocabulary)
      .catch(() => setVocabulary(null));
  }, []);

  if (!vocabulary) return null;

  // 이벤트 지표는 그 이벤트에서만 뜻이 있다 (#200 §4.1) — 고를 수 있는 것만 보인다.
  const usable = vocabulary.measures.filter((each) => each.events.includes(event));
  const existing = rules.find((rule) => rule.ruleKey === ruleKey.trim());
  const parsed = value === "true" || value === "false" ? value === "true" : Number(value);
  const body = {
    ruleKey: ruleKey.trim(),
    event,
    code: code.trim(),
    conditions: [{ measure, op, value: parsed }],
  };
  const ready = body.ruleKey !== "" && body.code !== "";

  const load = (rule: BadgeRule) => {
    setRuleKey(rule.ruleKey);
    setCode(rule.code);
    setEvent(rule.event);
    const first = rule.conditions[0];
    if (first) {
      setMeasure(first.measure);
      setOp(first.op);
      setValue(String(first.value));
    }
    setResult(null);
  };

  const preview = async () => {
    try {
      setResult(await badgeApi.dryRun(body, userId.trim() ? Number(userId.trim()) : undefined));
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "미리보기에 실패했습니다.");
    }
  };

  const save = async () => {
    setSaving(true);
    try {
      if (existing) await badgeApi.updateRule(existing.ruleKey, body);
      else await badgeApi.createRule(body);
      toast.success(existing ? "규칙을 고쳤습니다." : "규칙을 만들었습니다.");
      onSaved();
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="space-y-3 p-5">
      <div>
        <CardTitle>규칙 만들기 · 고치기</CardTitle>
        <p className="mt-1 text-xs text-ink-muted">
          아래 규칙 목록에서 하나를 누르면 여기로 실립니다. 새 키를 적으면 새로 만듭니다.
        </p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="규칙 키 (rule_key)">
          <Input value={ruleKey} placeholder="예: solved-100" onChange={(e) => setRuleKey(e.target.value)} />
        </Field>
        <Field label="줄 뱃지">
          {/* 뱃지 정의에서 고른다 — 손으로 치면 짝이 없는 규칙이 생긴다. */}
          <Select value={code} onChange={(e) => setCode(e.target.value)}>
            <option value="">고르세요</option>
            {badges.map((badge) => (
              <option key={badge.code} value={badge.code}>
                {badge.label} ({badge.code})
              </option>
            ))}
          </Select>
        </Field>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <Field label="이벤트">
          <Select value={event} onChange={(e) => setEvent(e.target.value)}>
            {vocabulary.events.map((each) => (
              <option key={each} value={each}>
                {each}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="지표">
          <Select value={measure} onChange={(e) => setMeasure(e.target.value)}>
            {usable.map((each) => (
              <option key={each.name} value={each.name}>
                {each.label}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="비교">
          <Select value={op} onChange={(e) => setOp(e.target.value)}>
            {vocabulary.operators.map((each) => (
              <option key={each} value={each}>
                {each}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="값">
          <Input value={value} onChange={(e) => setValue(e.target.value)} />
        </Field>
        <Field label="확인할 회원 ID (선택)">
          <Input inputMode="numeric" value={userId} onChange={(e) => setUserId(e.target.value)} />
        </Field>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Button variant="secondary" disabled={!ready} onClick={preview}>
          미리보기
        </Button>
        <Button disabled={!ready || saving} onClick={save}>
          {saving ? "저장 중…" : existing ? "고친 내용 저장" : "새 규칙 만들기"}
        </Button>
        {existing ? <span className="text-xs text-ink-muted">있는 규칙을 고칩니다.</span> : null}
      </div>

      <RuleList rules={rules} onPick={load} />
      <DryRunResult result={result} />
    </Card>
  );
}
