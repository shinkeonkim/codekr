"use client";

import { postApi } from "@/entities/post";
import type { Board, BoardOption } from "@/entities/post";
import { RequireAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { Alert, Button, Card, Field, Input, MarkdownEditor, Select, useToast } from "@/shared/ui";
import { useRouter, useSearchParams } from "next/navigation";
import { use, useEffect, useState } from "react";
import { PAGE_WIDTH } from "@/shared/ui/pageWidth";

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
  const searchParams = useSearchParams();
  // 문제 질문 탭에서 넘어오면 그 문제에 붙는다 (#139).
  const problemId = Number(searchParams.get("problemId")) || undefined;
  const [values, setValues] = useState({
    board: (problemId ? "QUESTION" : "FREE") as Board,
    title: "",
    body: "",
  });
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
        : await postApi.create({ ...values, problemId });
      toast.success("저장했습니다.");
      router.push(`/posts/${saved.summary.id}`);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className={`${PAGE_WIDTH.wide} space-y-4`} onSubmit={save}>
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
          {/*
            **버튼과 미리보기가 함께 온다** (#388). 질문 게시판의 대부분이 "이 코드가
            왜 틀렸나요" 인데(#139), 코드 블록을 못 감싸면 들여쓰기가 뭉개지고 답하는
            사람이 코드를 읽을 수 없다.
          */}
          <MarkdownEditor
            value={values.body}
            onChange={(body) => setValues({ ...values, body })}
            placeholder={"코드는 ``` 로 감쌉니다. 위의 '코드 블록' 버튼을 눌러도 됩니다."}
          />
        </Field>
      </Card>

      <Button type="submit" disabled={saving}>
        {saving ? "저장 중…" : "저장"}
      </Button>
    </form>
  );
}
