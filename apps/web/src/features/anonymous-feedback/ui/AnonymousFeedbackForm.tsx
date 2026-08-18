"use client";

import { ApiError, request } from "@/shared/api";
import { Button, Field, Select, Textarea, useToast } from "@/shared/ui";
import { useState } from "react";

const KINDS = [
  { value: "BUG", label: "안 되는 것을 알린다" },
  { value: "SUGGESTION", label: "이런 것이 있으면 좋겠다" },
];

/**
 * 로그인하지 못하는 사람이 알리는 자리 (#611).
 *
 * **가장 급한 신고가 로그인 바깥에 있다** — "가입이 안 됩니다", "인증 메일이 안 옵니다",
 * "비밀번호 재설정이 안 됩니다". 이 사람들은 로그인을 못 해서 알리려는 것인데, #603 이
 * 만든 통로는 로그인을 요구했다.
 *
 * **답을 돌려주지 않는다는 것을 미리 말한다.** 연락처를 받으면 가입 없이 개인정보를 모으는
 * 일이 되어 약관(#235)이 다루지 않는 자리가 된다. 기다리게 해 놓고 답이 없는 것보다,
 * 처음부터 "여기로는 답을 못 드린다" 고 적는 편이 정직하다.
 */
export function AnonymousFeedbackForm() {
  const toast = useToast();
  const [kind, setKind] = useState("BUG");
  const [body, setBody] = useState("");
  const [sending, setSending] = useState(false);
  const [done, setDone] = useState(false);

  if (done) {
    return (
      <p className="rounded-card border border-ok/40 bg-ok/5 p-4 text-sm text-ink">
        접수됐습니다. <strong>이 통로로는 답을 드릴 수 없습니다</strong> — 로그인할 수 있게 되면
        설정에서 처리 결과를 볼 수 있습니다.
      </p>
    );
  }

  const send = async () => {
    setSending(true);
    try {
      await request("/api/v1/feedbacks/anonymous", {
        method: "POST",
        // 어느 화면에서 겪었는지는 재현에 필요하다. 로그인 화면에서 왔다는 사실이 곧 단서다.
        body: { kind, body, pageUrl: typeof window === "undefined" ? null : window.location.href },
      });
      setDone(true);
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "보내지 못했습니다.");
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="space-y-3">
      <Field label="무엇을 알리시나요">
        <Select value={kind} onChange={(event) => setKind(event.target.value)}>
          {KINDS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>
      </Field>
      <Field label="내용">
        <Textarea
          rows={5}
          placeholder="예: 가입 후 인증 메일이 오지 않습니다. 주소는 you@example.com 입니다."
          value={body}
          onChange={(event) => setBody(event.target.value)}
        />
      </Field>
      {/* 답을 못 준다는 것을 **보내기 전에** 말한다. */}
      <p className="text-xs text-ink-muted">
        이 통로로는 답을 드릴 수 없습니다. 연락받을 주소를 내용에 적어 두시면 필요할 때
        저희가 그 주소로 연락드립니다.
      </p>
      <Button onClick={send} disabled={sending || body.trim().length === 0}>
        보내기
      </Button>
    </div>
  );
}
