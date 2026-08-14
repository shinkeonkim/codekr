"use client";

import { badgeApi } from "@/entities/badge";
import { ApiError } from "@/shared/api";
import { Button, Card, CardTitle, Field, Input, useToast } from "@/shared/ui";
import { useState } from "react";

/** 코드는 `user_badges` 에 박히는 값이라 서버가 모양을 못박는다. */
const CODE_PATTERN = /^[A-Z0-9_]{2,60}$/;

/**
 * 새 뱃지 정의 (#201, #549).
 *
 * **고칠 수는 있는데 만들 수 없었다.** `POST /admin/badges` 를 아무도 부르지 않아서,
 * 새 뱃지를 만들려면 DB 를 직접 만지거나 시드를 고쳐 배포해야 했다.
 *
 * **코드는 만든 뒤 못 바꾼다.** `user_badges` 에 박히는 값이라 바꾸면 이미 준 뱃지가
 * 가리키는 대상이 달라진다 — 서버가 `update` 에서 코드를 받지 않는 이유다.
 */
export function BadgeDefinitionForm({ onCreated }: { onCreated: () => void }) {
  const toast = useToast();
  const [code, setCode] = useState("");
  const [label, setLabel] = useState("");
  const [description, setDescription] = useState("");
  const [ruleKey, setRuleKey] = useState("");
  const [saving, setSaving] = useState(false);

  const valid = CODE_PATTERN.test(code) && label.trim() !== "" && description.trim() !== "" && ruleKey.trim() !== "";

  const create = async () => {
    setSaving(true);
    try {
      await badgeApi.createDefinition({
        code,
        label: label.trim(),
        description: description.trim(),
        ruleKey: ruleKey.trim(),
      });
      toast.success("뱃지를 만들었습니다. 이제 규칙을 붙이세요.");
      setCode("");
      setLabel("");
      setDescription("");
      setRuleKey("");
      onCreated();
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "만들지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="space-y-3 p-5">
      <div>
        <CardTitle>새 뱃지</CardTitle>
        <p className="mt-1 text-xs leading-relaxed text-ink-muted">
          만든 뒤 <span className="text-ink">코드는 바꿀 수 없습니다</span> — 이미 준 뱃지가
          그 코드를 가리킵니다. 규칙 키는 아래에서 만들 규칙과 같은 이름을 적으십시오.
        </p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="코드 (대문자·숫자·밑줄)">
          <Input value={code} placeholder="예: SOLVED_100" onChange={(e) => setCode(e.target.value.toUpperCase())} />
        </Field>
        <Field label="규칙 키">
          <Input value={ruleKey} placeholder="예: solved-100" onChange={(e) => setRuleKey(e.target.value)} />
        </Field>
        <Field label="이름">
          <Input value={label} placeholder="예: 백 문제" onChange={(e) => setLabel(e.target.value)} />
        </Field>
        <Field label="설명">
          <Input
            value={description}
            placeholder="예: 문제 100개를 맞혔습니다"
            onChange={(e) => setDescription(e.target.value)}
          />
        </Field>
      </div>

      <Button disabled={!valid || saving} onClick={create}>
        {saving ? "만드는 중…" : "뱃지 만들기"}
      </Button>
    </Card>
  );
}
