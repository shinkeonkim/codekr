"use client";

import { ApiError, request } from "@/shared/api";
import { Alert, Button, Field, Input, Textarea } from "@/shared/ui";
import { useEffect, useState } from "react";

interface Editorial {
  body: string;
  referenceAnswer: string | null;
  referenceLabel: string | null;
}

/**
 * 모범 답안 쓰기 (#719).
 *
 * **문제 폼과 따로 저장한다.** 서버에서도 다른 표이고 다른 길이다 — 채점에 쓰는 정답과
 * 한 덩어리로 묶으면, 설명을 다듬는 저장이 채점에 관여하는 저장과 구별되지 않는다.
 *
 * 그래서 **문제를 만든 뒤에만 보인다.** 아직 번호가 없으면 붙일 곳이 없다.
 */
export function EditorialEditor({ problemId }: { problemId: number }) {
  const [body, setBody] = useState("");
  const [answer, setAnswer] = useState("");
  const [label, setLabel] = useState("");
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    request<Editorial>(`/api/v1/admin/problems/${problemId}/editorial`, { auth: true })
      .then((next) => {
        if (!alive) return;
        setBody(next.body);
        setAnswer(next.referenceAnswer ?? "");
        setLabel(next.referenceLabel ?? "");
        setSaved(true);
      })
      // 아직 안 쓴 문제다. 빈 칸으로 시작하는 것이 맞다.
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, [problemId]);

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      await request(`/api/v1/admin/problems/${problemId}/editorial`, {
        method: "PUT",
        body: { body, referenceAnswer: answer, referenceLabel: label },
        auth: true,
      });
      setSaved(true);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "저장하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    setBusy(true);
    setError(null);
    try {
      await request(`/api/v1/admin/problems/${problemId}/editorial`, { method: "DELETE", auth: true });
      setBody("");
      setAnswer("");
      setLabel("");
      setSaved(false);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "지우지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      {error ? <Alert tone="danger">{error}</Alert> : null}
      <Field label="풀이 설명">
        <Textarea
          rows={8}
          value={body}
          onChange={(event) => setBody(event.target.value)}
          placeholder={"마크다운으로 씁니다.\n\n**푼 사람에게만 보입니다.** 대회가 도는 동안에는 아무에게도 안 보입니다."}
        />
      </Field>
      <div className="grid gap-4 sm:grid-cols-[1fr_200px]">
        <Field label="참고 답안 (선택)">
          <Textarea
            rows={5}
            value={answer}
            onChange={(event) => setAnswer(event.target.value)}
            /*
              **여기 적은 것은 아무도 실행하지 않는다.** 채점에 쓰는 정답은 유형별
              스펙 표에 따로 있다 — 그것을 다듬으면 채점 기준이 바뀐다.
            */
            placeholder="읽으라고 두는 것입니다. 채점에는 쓰이지 않습니다."
          />
        </Field>
        <Field label="그것이 무엇인지">
          <Input
            value={label}
            onChange={(event) => setLabel(event.target.value)}
            placeholder="python:3.12 · git 명령 · SQL"
          />
        </Field>
      </div>
      <div className="flex gap-2">
        <Button type="button" onClick={save} disabled={busy || !body.trim()}>
          {busy ? "저장 중…" : "모범 답안 저장"}
        </Button>
        {saved ? (
          <Button type="button" variant="danger" onClick={remove} disabled={busy}>
            지우기
          </Button>
        ) : null}
      </div>
    </div>
  );
}
