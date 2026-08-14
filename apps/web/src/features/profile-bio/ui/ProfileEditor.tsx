"use client";

import { userApi } from "@/entities/user";
import { ApiError } from "@/shared/api";
import { Button, Field, Input, Textarea, useToast } from "@/shared/ui";
import { useState } from "react";
import { BIO_MAX, type ProfileDraft, canSave, changes, problems } from "../model/draft";

/**
 * 남에게 보이는 프로필 — 이름과 소개 (#307, #310, #581).
 *
 * **저장 단추는 하나다.** 전에는 이름에 `이름 바꾸기`, 소개에 `저장` 이 따로 있었다.
 * 한 상자 안에서 둘 다 고친 사람은 **아래 것만 누르고 나간다** — 이름은 안 바뀌어
 * 있는데 화면은 아무 말도 하지 않는다. 되돌릴 것이 없으니 조용히 잃는다.
 *
 * 서버는 처음부터 둘을 함께 받았다(`PATCH /users/me/profile`). 나뉘어 있던 것은
 * 화면뿐이라, **한 번의 요청**으로 보낸다 — 그래야 절반만 저장되는 일이 없다.
 *
 * 무엇을 보내고 무엇을 막을지는 `model/draft.ts` 가 정한다.
 */
export function ProfileEditor({
  displayName,
  handle,
  bio,
  onChange,
}: {
  displayName: string;
  handle: string;
  bio: string | null;
  onChange: () => void;
}) {
  const toast = useToast();
  const saved: ProfileDraft = { displayName, bio: bio ?? "" };
  const [draft, setDraft] = useState<ProfileDraft>(saved);
  const [saving, setSaving] = useState(false);
  /** 서버만 아는 것 — 이미 쓰는 이름인지는 눌러 봐야 안다. */
  const [rejected, setRejected] = useState<Partial<ProfileDraft>>({});

  const found = { ...problems(draft, saved), ...rejected };
  const edit = (part: Partial<ProfileDraft>) => {
    setDraft((before) => ({ ...before, ...part }));
    // 고치기 시작하면 서버가 준 거절은 더 이상 지금 값에 대한 말이 아니다.
    setRejected({});
  };

  const save = async () => {
    setSaving(true);
    setRejected({});
    try {
      await userApi.updateProfile(changes(draft, saved));
      onChange();
      toast.success("프로필을 저장했습니다.");
    } catch (caught) {
      if (caught instanceof ApiError) {
        /*
          **어느 칸이 문제인지 보여 준다.** 토스트로만 알리면 사라진 뒤에 무엇을 고쳐야
          하는지 남지 않는다 — 특히 "이미 쓰는 이름" 은 그 칸을 다시 고쳐야 풀린다.
        */
        const byField = Object.fromEntries(
          caught.fieldErrors.map((each) => [each.field, each.message]),
        );
        const name = byField.displayName ?? (caught.code === "NICKNAME_ALREADY_EXISTS" ? caught.message : undefined);
        setRejected({ ...(name ? { displayName: name } : {}), ...(byField.bio ? { bio: byField.bio } : {}) });
      }
      toast.error(caught instanceof ApiError ? caught.message : "저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-3">
      {/* 이름은 바꿀 수 있고 주소는 그대로다 (#307). */}
      <Field label="표시 이름" error={found.displayName}>
        <Input
          value={draft.displayName}
          onChange={(event) => edit({ displayName: event.target.value })}
        />
      </Field>
      <p className="text-xs text-ink-muted">
        프로필 주소는 <span className="text-ink">/users/{handle}</span> 로 그대로입니다.
      </p>

      <Field label="소개" error={found.bio}>
        <Textarea
          rows={2}
          maxLength={BIO_MAX}
          value={draft.bio}
          onChange={(event) => edit({ bio: event.target.value })}
          placeholder="어떤 문제를 즐겨 푸는지, 무엇을 공부하는지"
        />
      </Field>

      <div className="flex items-center gap-3">
        {/* 남은 글자 수를 보인다 — 잘린 뒤에 아는 것보다 낫다. */}
        <span className="text-xs text-ink-muted tabular-nums">
          {draft.bio.length} / {BIO_MAX}
        </span>
        <Button
          className="ml-auto"
          disabled={saving || !canSave(draft, saved)}
          onClick={save}
        >
          저장
        </Button>
      </div>
    </div>
  );
}
