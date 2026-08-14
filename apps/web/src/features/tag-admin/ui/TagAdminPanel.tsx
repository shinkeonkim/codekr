"use client";

import { tagApi } from "@/entities/tag";
import type { Tag } from "@/entities/tag";
import { ApiError } from "@/shared/api";
import { Button, Card, Field, Input, useToast } from "@/shared/ui";
import { useEffect, useState } from "react";

/** 주소는 만든 뒤 바꿀 수 없다 — 링크와 필터 파라미터가 그것을 가리킨다. */
const SLUG_PATTERN = /^[a-z0-9-]{1,60}$/;

/**
 * 태그 만들기·고치기 (#232, #553).
 *
 * **API 만 있고 화면이 없으면 있는 줄도 모른다** (#180). 태그는 어드민만 만들 수 있으므로
 * 만드는 자리도 어드민에 있어야 한다.
 *
 * 만들기만 있던 동안 **이름의 오타를 고칠 방법이 없었다** (#553). 태그는 문제 목록·상세·
 * 검색 필터(#239)에 모두 나오므로 오타 하나가 그 모든 자리에 남았다.
 *
 * **주소는 못 고친다.** 링크와 필터 파라미터가 그것을 가리키므로 서버가 애초에 받지
 * 않는다 — 화면도 같은 말을 한다.
 */
export function TagAdminPanel() {
  const toast = useToast();
  const [tags, setTags] = useState<Tag[]>([]);
  const [slug, setSlug] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [saving, setSaving] = useState(false);
  /** 고치는 중인 태그. `null` 이면 만드는 중이다. */
  const [editing, setEditing] = useState<Tag | null>(null);

  const load = () => {
    tagApi.list().then(setTags).catch(() => setTags([]));
  };
  useEffect(load, []);

  // 고칠 때는 주소를 안 받으므로 이름만 본다.
  const valid = name.trim().length > 0 && (editing !== null || SLUG_PATTERN.test(slug));

  const reset = () => {
    setEditing(null);
    setSlug("");
    setName("");
    setDescription("");
  };

  const startEdit = (tag: Tag) => {
    setEditing(tag);
    setSlug(tag.slug);
    setName(tag.name);
    setDescription(tag.description ?? "");
  };

  const save = async () => {
    setSaving(true);
    try {
      const body = { name: name.trim(), description: description.trim() || undefined };
      if (editing) await tagApi.update(editing.id, body);
      else await tagApi.create({ slug, ...body });
      reset();
      load();
    } catch (caught) {
      const fallback = editing ? "태그를 고치지 못했습니다." : "태그를 만들지 못했습니다.";
      toast.error(caught instanceof ApiError ? caught.message : fallback);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="space-y-3 p-5">
      <div>
        <p className="font-medium text-ink">알고리즘 분류(태그)</p>
        <p className="mt-1 text-xs leading-relaxed text-ink-muted">
          어떤 기법으로 푸는 문제인지를 나타냅니다. 분야(무엇에 대한 문제인가)와는 다른
          축이라, 분야와 같은 뜻의 태그는 만들지 않습니다. 주소는 만든 뒤 바꿀 수 없습니다.
        </p>
      </div>

      <div className="grid gap-2 sm:grid-cols-2">
        <Field label="주소 (소문자·숫자·붙임표)">
          {/* 고칠 때는 잠근다. 서버가 받지 않는 값을 입력하게 두면 안 된다. */}
          <Input
            value={slug}
            disabled={editing !== null}
            placeholder="예: binary-search"
            onChange={(e) => setSlug(e.target.value)}
          />
        </Field>
        <Field label="이름">
          <Input value={name} placeholder="예: 이분 탐색" onChange={(e) => setName(e.target.value)} />
        </Field>
      </div>
      <Field label="설명 (비슷한 태그가 둘 생기는 것을 막습니다)">
        <Input
          value={description}
          placeholder="예: 정렬된 범위를 반씩 줄여 가며 찾는 기법"
          onChange={(e) => setDescription(e.target.value)}
        />
      </Field>

      <div className="flex flex-wrap items-center gap-2">
        <Button onClick={save} disabled={!valid || saving}>
          {saving ? "저장 중…" : editing ? "고친 내용 저장" : "태그 만들기"}
        </Button>
        {editing ? (
          <Button variant="secondary" onClick={reset} disabled={saving}>
            취소
          </Button>
        ) : null}
      </div>

      {tags.length > 0 ? (
        <div className="space-y-2 border-t border-border pt-3">
          <p className="text-xs text-ink-muted">
            눌러서 이름과 설명을 고칩니다. 옆의 숫자는 그 태그가 붙은 문제 수입니다 —
            고치기 전에 영향 범위를 알 수 있어야 합니다.
          </p>
          <div className="flex flex-wrap gap-1.5">
            {tags.map((tag) => (
              <button
                key={tag.id}
                type="button"
                onClick={() => startEdit(tag)}
                title={tag.description ?? undefined}
                aria-current={editing?.id === tag.id}
                className="rounded-full border border-border px-2.5 py-1 text-xs text-ink-muted hover:border-ink-muted aria-[current=true]:border-accent aria-[current=true]:text-ink"
              >
                {tag.name}
                <span className="ml-1 opacity-60">{tag.problemCount}</span>
              </button>
            ))}
          </div>
        </div>
      ) : null}
    </Card>
  );
}
