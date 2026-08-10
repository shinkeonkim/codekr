"use client";

import { contestApi } from "@/entities/contest";
import type { ContestNotice, ContestQuestion } from "@/entities/contest";
import { useAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Alert, Button, Card, Markdown, Textarea, useToast } from "@/shared/ui";
import { useEffect, useState } from "react";

/**
 * 대회 공지와 질의 (#147).
 *
 * 공지는 누구나 읽는다. 질의는 **서버가 볼 수 있는 것만 내려주므로** 화면은 거르지 않는다 —
 * 거르는 규칙이 두 곳에 있으면 갈라진다.
 */
export function ContestBoard({ slug, registered }: { slug: string; registered: boolean }) {
  const { user, isAdmin } = useAuth();
  const toast = useToast();
  const [notices, setNotices] = useState<ContestNotice[]>([]);
  const [questions, setQuestions] = useState<ContestQuestion[]>([]);
  const [draft, setDraft] = useState("");

  const load = () => {
    contestApi.notices(slug).then(setNotices).catch(() => setNotices([]));
    contestApi.questions(slug).then(setQuestions).catch(() => setQuestions([]));
  };

  useEffect(load, [slug]);

  const ask = async () => {
    try {
      await contestApi.ask(slug, { body: draft });
      setDraft("");
      toast.success("질문을 남겼습니다.");
      load();
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "질문하지 못했습니다.");
    }
  };

  return (
    <div className="space-y-5">
      <section className="space-y-2">
        <h2 className="text-sm font-semibold text-ink">공지 {notices.length}건</h2>
        {notices.length === 0 ? (
          <p className="text-xs text-ink-muted">아직 공지가 없습니다.</p>
        ) : (
          notices.map((notice) => (
            <Card key={notice.id} className="space-y-2 p-4">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-medium text-ink">{notice.title}</span>
                <span className="text-xs text-ink-muted">{formatDateTime(notice.createdAt)}</span>
              </div>
              <Markdown source={notice.body} />
            </Card>
          ))
        )}
      </section>

      <section className="space-y-2">
        <h2 className="text-sm font-semibold text-ink">질의</h2>

        {/* 참가자만 묻는다. 등록하지 않은 사람은 문제도 볼 수 없다. */}
        {user && registered ? (
          <div className="space-y-2">
            <Textarea
              rows={3}
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder="문제나 규칙에 대해 물어보세요"
            />
            <Button className="px-3 py-1 text-xs" disabled={!draft.trim()} onClick={ask}>
              질문하기
            </Button>
          </div>
        ) : (
          <Alert>참가 등록한 사람만 질문할 수 있습니다.</Alert>
        )}

        {questions.length === 0 ? (
          <p className="text-xs text-ink-muted">
            {/* 왜 비어 있는지 말한다. 남의 질문이 안 보이는 것은 규칙이지 고장이 아니다. */}
            보이는 질의가 없습니다. 남의 질의는 공개 답변이 달린 뒤에 보입니다.
          </p>
        ) : (
          questions.map((question) => (
            <Card key={question.id} className="space-y-2 p-4">
              <div className="flex flex-wrap items-center gap-2 text-xs text-ink-muted">
                {question.problemLabel ? <span className="text-ink">{question.problemLabel}</span> : null}
                <span>{formatDateTime(question.createdAt)}</span>
                {question.mine ? <span className="text-brand">내 질문</span> : null}
                {question.answerPublic ? <span>공개 답변</span> : null}
              </div>
              <Markdown source={question.body} />
              {question.answer ? (
                <div className="rounded-lg border border-border bg-surface-muted p-3">
                  <p className="mb-1 text-xs font-medium text-ink">운영자 답변</p>
                  <Markdown source={question.answer} />
                </div>
              ) : (
                <p className="text-xs text-ink-muted">아직 답변이 없습니다.</p>
              )}
              {isAdmin && !question.answer ? <AnswerForm slug={slug} question={question} onDone={load} /> : null}
            </Card>
          ))
        )}
      </section>
    </div>
  );
}

function AnswerForm({
  slug,
  question,
  onDone,
}: {
  slug: string;
  question: ContestQuestion;
  onDone: () => void;
}) {
  const toast = useToast();
  const [answer, setAnswer] = useState("");
  // 기본은 비공개다. **공개는 되돌릴 수 없다** — 이미 본 사람에게서 지울 수 없다.
  const [isPublic, setPublic] = useState(false);

  const submit = async () => {
    try {
      await contestApi.answer(slug, question.id, { answer, public: isPublic });
      toast.success("답변했습니다.");
      onDone();
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "답변하지 못했습니다.");
    }
  };

  return (
    <div className="space-y-2 border-t border-border pt-2">
      <Textarea
        rows={2}
        value={answer}
        onChange={(event) => setAnswer(event.target.value)}
        placeholder="답변"
      />
      <label className="flex items-center gap-2 text-xs text-ink">
        <input type="checkbox" checked={isPublic} onChange={(event) => setPublic(event.target.checked)} />
        전원에게 공개합니다 (되돌릴 수 없습니다)
      </label>
      <Button className="px-3 py-1 text-xs" disabled={!answer.trim()} onClick={submit}>
        답변하기
      </Button>
    </div>
  );
}
