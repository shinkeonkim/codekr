"use client";

import { userApi } from "@/entities/user";
import { ApiError } from "@/shared/api";
import { Button, Field, Input, useToast } from "@/shared/ui";
import { useState } from "react";

/**
 * 표시 이름 바꾸기 (#307).
 *
 * **주소가 따로 있어서 바꿀 수 있게 됐다.** 전에는 이름이 곧 주소라, 바꾸면 주고받은
 * 링크와 검색 색인(#278)이 끊겼다 — 그래서 아예 못 바꿨다.
 */
export function DisplayNameEditor({
  displayName,
  handle,
  onChange,
}: {
  displayName: string;
  handle: string;
  onChange: () => void;
}) {
  const toast = useToast();
  const [value, setValue] = useState(displayName);
  const [saving, setSaving] = useState(false);

  const save = async () => {
    setSaving(true);
    try {
      await userApi.updateProfile({ displayName: value.trim() });
      onChange();
      toast.success("이름을 바꿨습니다.");
    } catch (caught) {
      // 이미 쓰는 이름이면 서버가 그렇게 알린다 — 500 이 아니다.
      toast.error(caught instanceof ApiError ? caught.message : "바꾸지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-2">
      <Field label="표시 이름">
        <Input value={value} onChange={(event) => setValue(event.target.value)} />
      </Field>
      {/* **주소는 바뀌지 않는다** — 그것을 여기서 분명히 말한다. */}
      <p className="text-xs text-ink-muted">
        프로필 주소는 <span className="text-ink">/users/{handle}</span> 로 그대로입니다.
      </p>
      <Button
        variant="secondary"
        disabled={saving || value.trim().length < 2 || value.trim() === displayName}
        onClick={save}
      >
        이름 바꾸기
      </Button>
    </div>
  );
}
