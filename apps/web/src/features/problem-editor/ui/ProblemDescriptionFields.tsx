"use client";

import { Button, Field, Markdown, Textarea } from "@/shared/ui";
import { useState } from "react";
import type { ProblemFormValues } from "../model/values";

/**
 * 지문 (#127, #338). 문제 설명과 입출력 설명.
 *
 * **마크다운으로 그려진다.** 그래서 미리보기가 필요하다 — 마크다운을 여는 순간
 * "쓴 대로 보이지 않는" 일이 생기고, 그것을 저장 전에 확인할 길이 없으면 출제자는
 * 등록하고 나서야 알게 된다. 게시글 편집기(#137)가 같은 이유로 같은 것을 둔다.
 *
 * **`Card` 로 감싸지 않는다** — 바깥의 `FormSection`(#337)이 이미 상자다.
 */
export function ProblemDescriptionFields({
  values,
  onChange,
}: {
  values: ProblemFormValues;
  onChange: <K extends keyof ProblemFormValues>(key: K, value: ProblemFormValues[K]) => void;
}) {
  const update = onChange;
  const [preview, setPreview] = useState(false);

  return (
    <div className="space-y-4">
      <Field label="문제 설명">
        <Textarea
          rows={8}
          value={values.description}
          onChange={(event) => update("description", event.target.value)}
          placeholder={"마크다운으로 씁니다.\n\n**굵게**, 목록, 그리고 표 구조는 코드 블록으로:\n\n```\nmembers(id, name, city)\n```"}
          required
        />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="입력 형식">
          <Textarea
            rows={3}
            value={values.inputDescription}
            onChange={(event) => update("inputDescription", event.target.value)}
          />
        </Field>
        <Field label="출력 형식">
          <Textarea
            rows={3}
            value={values.outputDescription}
            onChange={(event) => update("outputDescription", event.target.value)}
          />
        </Field>
      </div>

      <Button type="button" variant="secondary" className="px-3 py-1 text-xs" onClick={() => setPreview((it) => !it)}>
        {preview ? "미리보기 닫기" : "미리보기"}
      </Button>
      {preview ? (
        /*
          **상세 화면과 같은 순서로 보여 준다** — 문제·입력·출력. 미리보기가 다른
          순서로 보이면 그것은 미리보기가 아니다.
        */
        <div className="space-y-4 rounded-lg border border-border p-4">
          <PreviewBlock title="문제" body={values.description} />
          <PreviewBlock title="입력" body={values.inputDescription} />
          <PreviewBlock title="출력" body={values.outputDescription} />
        </div>
      ) : null}
    </div>
  );
}

function PreviewBlock({ title, body }: { title: string; body: string }) {
  if (!body.trim()) return null;
  return (
    <div>
      <h3 className="mb-1.5 text-sm font-semibold text-ink">{title}</h3>
      <div className="text-sm text-ink-muted">
        <Markdown source={body} />
      </div>
    </div>
  );
}
