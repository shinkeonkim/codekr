"use client";

import { Button, Textarea } from "@/shared/ui";
import { useState } from "react";

/** 댓글·답글 입력창 (#138). */
export function CommentForm({
  onSubmit,
  placeholder,
}: {
  onSubmit: (body: string) => void;
  placeholder: string;
}) {
  const [body, setBody] = useState("");

  return (
    <form
      className="space-y-2"
      onSubmit={(event) => {
        event.preventDefault();
        if (!body.trim()) return;
        onSubmit(body);
        setBody("");
      }}
    >
      <Textarea
        rows={3}
        value={body}
        onChange={(event) => setBody(event.target.value)}
        placeholder={placeholder}
      />
      <Button type="submit" className="px-3 py-1 text-xs" disabled={!body.trim()}>
        남기기
      </Button>
    </form>
  );
}
