"use client";

import { problemApi } from "@/entities/problem";
import type { ProblemTemplate, Runtime } from "@/entities/problem";
import { useEffect, useState } from "react";
import { Button, Select, Textarea } from "@/shared/ui";

interface Props {
  templates: ProblemTemplate[];
  onChange: (templates: ProblemTemplate[]) => void;
}

/**
 * 언어/버전별 초기 코드 편집기.
 *
 * 지정하지 않은 실행 환경은 런타임 레지스트리의 기본 템플릿을 그대로 쓰므로,
 * 문제마다 필요한 언어만 골라 넣으면 된다.
 */
export function ProblemTemplateEditor({ templates, onChange }: Props) {
  const [runtimes, setRuntimes] = useState<Runtime[]>([]);
  const [selected, setSelected] = useState("");

  useEffect(() => {
    problemApi.runtimes().then(setRuntimes).catch(() => setRuntimes([]));
  }, []);

  const available = runtimes.filter((runtime) => !templates.some((it) => it.runtimeId === runtime.id));
  const labelOf = (runtimeId: string) => runtimes.find((it) => it.id === runtimeId)?.label ?? runtimeId;

  const add = () => {
    const runtime = runtimes.find((it) => it.id === selected) ?? available[0];
    if (!runtime) return;
    // 새로 추가할 때는 런타임 기본 템플릿에서 출발하는 편이 편집하기 쉽다.
    onChange([...templates, { runtimeId: runtime.id, sourceCode: runtime.template }]);
    setSelected("");
  };

  const update = (runtimeId: string, sourceCode: string) =>
    onChange(templates.map((it) => (it.runtimeId === runtimeId ? { ...it, sourceCode } : it)));

  const remove = (runtimeId: string) => onChange(templates.filter((it) => it.runtimeId !== runtimeId));

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <div>
                    <p className="mt-0.5 text-xs text-ink-muted">
            지정하지 않은 언어는 기본 템플릿이 제공됩니다.
          </p>
        </div>
        <Select
          className="ml-auto w-56"
          value={selected}
          onChange={(event) => setSelected(event.target.value)}
          disabled={available.length === 0}
        >
          <option value="">추가할 언어 선택</option>
          {available.map((runtime) => (
            <option key={runtime.id} value={runtime.id}>
              {runtime.label}
            </option>
          ))}
        </Select>
        <Button type="button" variant="secondary" onClick={add} disabled={available.length === 0}>
          추가
        </Button>
      </div>

      {templates.length === 0 ? (
        <p className="rounded-lg border border-dashed border-border px-4 py-6 text-center text-sm text-ink-muted">
          아직 지정한 초기 코드가 없습니다.
        </p>
      ) : null}

      {templates.map((template) => (
        <div key={template.runtimeId} className="space-y-2 rounded-lg border border-border p-4">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-ink">{labelOf(template.runtimeId)}</span>
            <Button
              type="button"
              variant="danger"
              className="ml-auto"
              onClick={() => remove(template.runtimeId)}
            >
              삭제
            </Button>
          </div>
          <Textarea
            rows={8}
            value={template.sourceCode}
            onChange={(event) => update(template.runtimeId, event.target.value)}
          />
        </div>
      ))}
    </div>
  );
}
