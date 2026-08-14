"use client";

import { REPORT_KIND_LABELS, problemReportApi } from "@/entities/problem-report";
import type { ReportKind } from "@/entities/problem-report";
import { useAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { Button, Card, Field, Select, Textarea, useToast } from "@/shared/ui";
import { useState } from "react";

const KINDS = Object.keys(REPORT_KIND_LABELS) as ReportKind[];

/**
 * 문제의 오류를 신고한다 (#478, #548).
 *
 * ## 질문과 가른다
 *
 * **"내가 틀린 것 같으면 질문, 문제가 틀린 것 같으면 신고"** 다. 질문은 다른 사용자가
 * 답하고 신고는 어드민만 처리한다 — 섞이면 질문 백 개 사이에 신고 하나가 묻힌다.
 *
 * 그래서 질문 탭 안에 두되 **눈에 덜 띄는 자리**에 둔다. 앞에 두면 질문 대신 신고를
 * 누르는 사람이 생긴다.
 */
export function ProblemReportDialog({ slug }: { slug: string }) {
  const { user } = useAuth();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [kind, setKind] = useState<ReportKind>("WRONG_STATEMENT");
  const [body, setBody] = useState("");
  const [saving, setSaving] = useState(false);

  // 로그인하지 않았으면 신고할 수 없다 — 서버가 막는다. 미리 말해 준다.
  if (!user) return null;

  const submit = async () => {
    setSaving(true);
    try {
      await problemReportApi.report(slug, { kind, body: body.trim() });
      toast.success("신고를 접수했습니다. 확인 뒤 결과를 반영합니다.");
      setBody("");
      setOpen(false);
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "신고하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="text-xs text-ink-muted underline-offset-2 hover:text-ink hover:underline"
      >
        문제 자체가 틀린 것 같나요? 오류 신고
      </button>
    );
  }

  return (
    <Card className="space-y-3 p-4">
      <p className="text-sm font-medium text-ink">문제 오류 신고</p>
      <p className="text-xs leading-relaxed text-ink-muted">
        <span className="text-ink">지문·테스트케이스·정답이 잘못된 것 같을 때</span> 씁니다.
        푸는 방법을 묻는 것은 질문에 남겨 주세요 — 신고는 어드민만 봅니다.
      </p>

      <Field label="어떤 문제입니까">
        <Select value={kind} onChange={(event) => setKind(event.target.value as ReportKind)}>
          {KINDS.map((each) => (
            <option key={each} value={each}>
              {REPORT_KIND_LABELS[each]}
            </option>
          ))}
        </Select>
      </Field>

      <Field label="무엇이 어떻게 잘못됐습니까">
        <Textarea
          rows={4}
          value={body}
          placeholder="예: 입력에 N=0 이 올 수 있다고 적혀 있는데 그 경우의 기대 출력이 없습니다."
          onChange={(event) => setBody(event.target.value)}
        />
      </Field>

      <div className="flex flex-wrap gap-2">
        <Button disabled={saving || body.trim().length < 5} onClick={submit}>
          {saving ? "보내는 중…" : "신고하기"}
        </Button>
        <Button variant="secondary" disabled={saving} onClick={() => setOpen(false)}>
          취소
        </Button>
      </div>
    </Card>
  );
}
