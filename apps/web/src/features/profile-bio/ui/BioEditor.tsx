"use client";

import { userApi } from "@/entities/user";
import { ApiError } from "@/shared/api";
import { Button, Field, Textarea, useToast } from "@/shared/ui";
import { useState } from "react";

/** 서버의 상한과 같아야 한다 (#310). 넘으면 400 이 오는데, 그 전에 화면이 말해 준다. */
const MAX_LENGTH = 100;

/**
 * 소개 문구 편집 (#310).
 *
 * 아바타(#116)와 성격이 같다 — 본인이 쓰고, 남에게 보이고, 설정에서 바꾼다.
 * 그래서 같은 자리에 둔다.
 */
export function BioEditor({ bio, onChange }: { bio: string | null; onChange: () => void }) {
  const toast = useToast();
  const [value, setValue] = useState(bio ?? "");
  const [saving, setSaving] = useState(false);

  const save = async () => {
    setSaving(true);
    try {
      await userApi.updateProfile({ bio: value });
      // 서버가 앞뒤 공백을 다듬으므로, 저장한 값을 화면이 다시 받아 온다.
      onChange();
      toast.success(value.trim() ? "소개를 저장했습니다." : "소개를 지웠습니다.");
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const over = value.length > MAX_LENGTH;

  return (
    <div className="space-y-2">
      <Field label="소개" error={over ? `${MAX_LENGTH}자를 넘었습니다.` : undefined}>
        <Textarea
          rows={2}
          maxLength={MAX_LENGTH}
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder="어떤 문제를 즐겨 푸는지, 무엇을 공부하는지"
        />
      </Field>
      <div className="flex items-center gap-3">
        {/* 남은 글자 수를 보인다 — 잘린 뒤에 아는 것보다 낫다. */}
        <span className="text-xs text-ink-muted tabular-nums">
          {value.length} / {MAX_LENGTH}
        </span>
        <Button className="ml-auto" disabled={saving || over} onClick={save}>
          저장
        </Button>
      </div>
    </div>
  );
}
