"use client";

import { adminContestApi } from "@/entities/contest";
import type { AdminContest, ContestUpsert } from "@/entities/contest";
import { ApiError } from "@/shared/api";
import { Alert, Button, Card, Field, Input, Textarea, useToast } from "@/shared/ui";
import { ContestAdminPanels } from "./ContestAdminPanels";
import { ContestPolicyFields } from "./ContestPolicyFields";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

const BLANK: ContestUpsert = {
  slug: "",
  title: "",
  description: "",
  startsAt: "",
  endsAt: "",
  // 기본값은 서버와 같다 (#86, #189) — 화면이 다른 값을 권하면 두 곳이 갈린다.
  freezeMinutes: 30,
  submissionCooldownSeconds: 20,
  // 기본은 공개다 (#465). 기본을 바꾸면 이 판 이후에 만든 대회만 조용히 숨는다.
  visibility: "PUBLIC",
  // 기본은 승인 없이 참가다 (#466). 켜면 운영자가 한 명씩 승인해야 한다.
  requiresApproval: false,
};

/**
 * 대회 등록·수정 (#335).
 *
 * **문제 붙이기는 여기 없다.** 대회 하나에 문제가 여럿이라 한 폼에 다 넣으면 길어지고,
 * #337 이 문제 폼에서 겪는 것과 같은 문제가 된다 — 만든 뒤에 대회 화면에서 붙인다.
 */
export function AdminContestFormPage({ id }: { id?: number }) {
  const router = useRouter();
  const toast = useToast();
  const [values, setValues] = useState<ContestUpsert>(BLANK);
  /** 운영 패널이 쓰는 서버 상태. 폼 값과 달리 동결·문제 목록까지 들고 있다. */
  const [contest, setContest] = useState<AdminContest | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!id) return;
    adminContestApi
      .detail(id)
      .then((contest) => {
        setContest(contest);
        setValues({
          slug: contest.slug,
          title: contest.title,
          description: contest.description,
          // datetime-local 은 초·시간대를 빼고 받는다.
          startsAt: contest.startsAt.slice(0, 16),
          endsAt: contest.endsAt.slice(0, 16),
          freezeMinutes: contest.freezeMinutes,
          submissionCooldownSeconds: contest.submissionCooldownSeconds,
          visibility: contest.visibility,
          requiresApproval: contest.requiresApproval,
        });
      })
      .catch(() => setError("대회를 불러오지 못했습니다."));
  }, [id]);

  const update = <K extends keyof ContestUpsert>(key: K, value: ContestUpsert[K]) =>
    setValues((current) => ({ ...current, [key]: value }));

  const submit = async () => {
    setSaving(true);
    try {
      const body = {
        ...values,
        // 로컬 시각으로 받은 것을 그대로 보내면 서버가 UTC 로 읽는다.
        startsAt: new Date(values.startsAt).toISOString(),
        endsAt: new Date(values.endsAt).toISOString(),
      };
      if (id) await adminContestApi.update(id, body);
      else await adminContestApi.create(body);
      toast.success("저장했습니다.");
      router.push("/admin/contests");
    } catch (caught) {
      const message = caught instanceof ApiError ? caught.message : "저장하지 못했습니다.";
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-ink">{id ? "대회 수정" : "새 대회"}</h1>
      {/* 서버가 막는 이유(진행 중)도 여기로 온다 — 문구를 그대로 보인다. */}
      {error ? <Alert tone="danger">{error}</Alert> : null}

      <Card className="space-y-3 p-5">
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="slug (URL 식별자)">
            <Input value={values.slug} onChange={(e) => update("slug", e.target.value)} />
          </Field>
          <Field label="제목">
            <Input value={values.title} onChange={(e) => update("title", e.target.value)} />
          </Field>
          <Field label="시작">
            <Input
              type="datetime-local"
              value={values.startsAt}
              onChange={(e) => update("startsAt", e.target.value)}
            />
          </Field>
          <Field label="종료">
            <Input
              type="datetime-local"
              value={values.endsAt}
              onChange={(e) => update("endsAt", e.target.value)}
            />
          </Field>
          <Field label="순위 동결 (분, 0이면 동결하지 않음)">
            <Input
              inputMode="numeric"
              value={String(values.freezeMinutes)}
              onChange={(e) => update("freezeMinutes", Number(e.target.value) || 0)}
            />
          </Field>
          <Field label="제출 간격 (초, 3 이상)">
            <Input
              inputMode="numeric"
              value={String(values.submissionCooldownSeconds)}
              onChange={(e) => update("submissionCooldownSeconds", Number(e.target.value) || 0)}
            />
          </Field>
        </div>
        <ContestPolicyFields values={values} update={update} />
        <Field label="설명">
          <Textarea
            rows={4}
            value={values.description}
            onChange={(e) => update("description", e.target.value)}
          />
        </Field>
        <Button disabled={saving || !values.slug.trim() || !values.title.trim()} onClick={submit}>
          저장
        </Button>
      </Card>

      {/* 만들기 전에는 신청자도 운영할 것도 감사할 것도 없다. */}
      {contest ? (
        <ContestAdminPanels
          contest={contest}
          requiresApproval={values.requiresApproval}
          onChanged={setContest}
        />
      ) : null}
    </div>
  );
}
