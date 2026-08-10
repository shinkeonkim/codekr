"use client";

import { postApi } from "@/entities/post";
import type { Board, BoardOption } from "@/entities/post";
import { RequireAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { Alert, Button, Card, Field, Input, Markdown, Select, Textarea, useToast } from "@/shared/ui";
import { useRouter } from "next/navigation";
import { use, useEffect, useState } from "react";

export function PostNewPage() {
  return (
    <RequireAuth>
      <Editor title="새 글" />
    </RequireAuth>
  );
}

export function PostEditPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <RequireAuth>
      <Editor title="글 수정" postId={Number(id)} />
    </RequireAuth>
  );
}

function Editor({ title, postId }: { title: string; postId?: number }) {
  const router = useRouter();
  const toast = useToast();
  const [boards, setBoards] = useState<BoardOption[]>([]);
  const [values, setValues] = useState({ board: "FREE" as Board, title: "", body: "" });
  const [preview, setPreview] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    postApi.boards().then(setBoards).catch(() => setBoards([]));
  }, []);

  useEffect(() => {
    if (!postId) return;
    postApi
      .detail(postId)
      .then((post) =>
        setValues({ board: post.summary.board, title: post.summary.title, body: post.body }),
      )
      .catch(() => setError("글을 불러오지 못했습니다."));
  }, [postId]);

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const saved = postId
        ? await postApi.update(postId, values)
        : await postApi.create(values);
      toast.success("저장했습니다.");
      router.push(`/posts/${saved.summary.id}`);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="mx-auto max-w-3xl space-y-4" onSubmit={save}>
      <h1 className="text-2xl font-bold text-ink">{title}</h1>
      {error ? <Alert>{error}</Alert> : null}

      <Card className="space-y-4 p-5">
        <Field label="게시판">
          <Select
            value={values.board}
            onChange={(event) => setValues({ ...values, board: event.target.value as Board })}
          >
            {/* 쓸 수 없는 게시판은 아예 고를 수 없다. */}
            {boards.filter((it) => it.writable).map((option) => (
              <option key={option.value} value={option.value}>
                {option.label} — {option.description}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="제목">
          <Input
            value={values.title}
            onChange={(event) => setValues({ ...values, title: event.target.value })}
            required
          />
        </Field>
        <Field label="본문 (마크다운)">
          <Textarea
            rows={14}
            className="font-mono text-xs"
            value={values.body}
            onChange={(event) => setValues({ ...values, body: event.target.value })}
            placeholder={"코드는 ``` 로 감쌉니다.\n\n```py\nprint(1)\n```"}
            required
          />
        </Field>
        {/* 마크다운을 처음 쓰는 사람이 결과를 저장 전에 볼 수 있어야 한다. */}
        <Button type="button" variant="secondary" className="px-3 py-1 text-xs" onClick={() => setPreview((it) => !it)}>
          {preview ? "미리보기 닫기" : "미리보기"}
        </Button>
        {preview ? (
          <div className="rounded-lg border border-border p-4">
            <Markdown source={values.body} />
          </div>
        ) : null}
      </Card>

      <Button type="submit" disabled={saving}>
        {saving ? "저장 중…" : "저장"}
      </Button>
    </form>
  );
}
