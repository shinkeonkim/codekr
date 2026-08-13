"use client";

import { tagApi } from "@/entities/tag";
import type { ProblemTag, Tag } from "@/entities/tag";
import { ApiError } from "@/shared/api";
import { Button, Card, CardTitle, useToast } from "@/shared/ui";
import { useEffect, useState } from "react";

/**
 * 문제에 태그 달기 (#232).
 *
 * **문제 폼과 따로 저장한다.** 태그는 별도 엔드포인트(`PUT …/tags`)로 통째로 바뀌고,
 * 새 문제에는 아직 id 가 없어 달 수 없다. 폼 안에 넣으면 "저장을 눌러야 태그가 붙는다"
 * 와 "저장 없이도 붙는다" 가 한 화면에 섞인다.
 */
export function ProblemTagEditor({ problemId, initial }: { problemId: number; initial: ProblemTag[] }) {
  const toast = useToast();
  const [tags, setTags] = useState<Tag[]>([]);
  const [picked, setPicked] = useState<number[]>(() => initial.map((tag) => tag.id));
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    tagApi.list().then(setTags).catch(() => setTags([]));
  }, []);

  const toggle = (id: number) => {
    setSaved(false);
    setPicked((current) => (current.includes(id) ? current.filter((it) => it !== id) : [...current, id]));
  };

  const save = async () => {
    setSaving(true);
    try {
      await tagApi.replaceProblemTags(problemId, picked);
      setSaved(true);
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "태그를 저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="space-y-3 p-5">
      <div>
        <CardTitle>알고리즘 분류</CardTitle>
        <p className="mt-1 text-xs leading-relaxed text-ink-muted">
          어떤 기법으로 푸는 문제인지 고릅니다. 카테고리(무엇에 대한 문제인가)와는 다른
          축입니다. 푸는 사람 화면에서는 접혀 있다가 펼쳤을 때만 보입니다.
        </p>
      </div>

      {tags.length === 0 ? (
        <p className="text-xs text-ink-muted">아직 만들어진 태그가 없습니다.</p>
      ) : (
        <div className="flex flex-wrap gap-1.5">
          {tags.map((tag) => {
            const on = picked.includes(tag.id);
            return (
              <button
                key={tag.id}
                type="button"
                onClick={() => toggle(tag.id)}
                title={tag.description ?? undefined}
                className={`rounded-full border px-2.5 py-1 text-xs transition ${
                  on
                    ? "border-brand bg-brand/10 text-brand"
                    : "border-border text-ink-muted hover:text-ink"
                }`}
              >
                {tag.name}
                {/* 붙은 문제 수를 함께 보여 준다 — 비슷한 태그가 둘 생겼는지 여기서 드러난다. */}
                <span className="ml-1 opacity-60">{tag.problemCount}</span>
              </button>
            );
          })}
        </div>
      )}

      <div className="flex items-center gap-2">
        <Button onClick={save} disabled={saving}>
          {saving ? "저장 중…" : "태그 저장"}
        </Button>
        {/* 문제 본문과 따로 저장되므로, 저장됐다는 사실을 이 자리에서 알려야 한다. */}
        {saved ? <span className="text-xs text-ok">저장했습니다.</span> : null}
      </div>
    </Card>
  );
}
