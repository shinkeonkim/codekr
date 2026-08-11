"use client";

import { Avatar, userApi } from "@/entities/user";
import { ApiError } from "@/shared/api";
import { Button, useToast } from "@/shared/ui";
import { useRef, useState } from "react";

/**
 * 아바타 올리기·바꾸기·지우기 (#116). **본인일 때만 보인다.**
 *
 * 고른 즉시 미리보기를 보여준다 — 올리고 나서야 결과를 알면 마음에 안 들 때
 * 다시 고르는 일이 두 번 든다.
 */
export function AvatarEditor({
  nickname,
  avatarUrl,
  onChange,
}: {
  nickname: string;
  avatarUrl: string | null;
  onChange: (next: string | null) => void;
}) {
  const toast = useToast();
  const input = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const pick = async (file: File | undefined) => {
    if (!file) return;
    // 브라우저가 만든 임시 주소. 서버에 올리기 전에 보여준다.
    const local = URL.createObjectURL(file);
    setPreview(local);
    setBusy(true);
    try {
      const { avatarUrl: next } = await userApi.uploadAvatar(file);
      onChange(next);
      toast.success("아바타를 바꿨습니다.");
    } catch (caught) {
      // 실패하면 미리보기를 걷어낸다. 남겨 두면 바뀐 것처럼 보인다.
      setPreview(null);
      toast.error(caught instanceof ApiError ? caught.message : "올리지 못했습니다.");
    } finally {
      setBusy(false);
      URL.revokeObjectURL(local);
      if (input.current) input.current.value = "";
    }
  };

  const remove = async () => {
    setBusy(true);
    try {
      await userApi.removeAvatar();
      setPreview(null);
      onChange(null);
      toast.success("아바타를 지웠습니다.");
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "지우지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex items-center gap-4">
      <Avatar nickname={nickname} avatarUrl={preview ?? avatarUrl} size="lg" />
      <div className="space-y-2">
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant="secondary"
            disabled={busy}
            onClick={() => input.current?.click()}
          >
            {busy ? "올리는 중…" : avatarUrl ? "바꾸기" : "이미지 올리기"}
          </Button>
          {avatarUrl ? (
            <Button type="button" variant="danger" disabled={busy} onClick={remove}>
              지우기
            </Button>
          ) : null}
        </div>
        {/* 무엇이 되는지 미리 말한다. 올린 뒤에 거부당하면 왜인지 모른다. */}
        <p className="text-xs text-ink-muted">
          정사각형으로 잘려 저장됩니다. 이미지 파일만, 5MB 이하.
        </p>
      </div>

      <input
        ref={input}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(event) => pick(event.target.files?.[0])}
      />
    </div>
  );
}
