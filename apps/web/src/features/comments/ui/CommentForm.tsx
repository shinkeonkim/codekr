"use client";

import { Button } from "@/shared/ui";
import { useState } from "react";
import type { MentionLabel } from "../model/mentionText";
import { toStored } from "../model/mentionText";
import { MentionTextarea } from "./MentionTextarea";

/** 댓글·답글 입력창 (#138) 과 멘션 (#214). */
export function CommentForm({
  onSubmit,
  placeholder,
}: {
  onSubmit: (body: string) => void;
  placeholder: string;
}) {
  const [body, setBody] = useState("");
  // **고른 사람만 멘션이다.** 손으로 친 `@아무개` 는 그냥 글자로 남는다.
  const [picked, setPicked] = useState<MentionLabel[]>([]);

  return (
    <form
      className="space-y-2"
      onSubmit={(event) => {
        event.preventDefault();
        if (!body.trim()) return;
        onSubmit(toStored(body, picked));
        setBody("");
        setPicked([]);
      }}
    >
      <MentionTextarea
        value={body}
        onChange={setBody}
        onPick={(label) => setPicked((current) => [...current, label])}
        placeholder={placeholder}
      />
      <Button type="submit" className="px-3 py-1 text-xs" disabled={!body.trim()}>
        남기기
      </Button>
    </form>
  );
}
