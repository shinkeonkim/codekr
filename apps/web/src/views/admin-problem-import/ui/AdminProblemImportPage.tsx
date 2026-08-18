"use client";

import { problemApi } from "@/entities/problem";
import type { ProblemBundlePreview } from "@/entities/problem";
import { ApiError } from "@/shared/api";
import { Alert, Badge, Button, Card, CardTitle } from "@/shared/ui";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { isBlocked } from "../model/warnings";
import { ImportPreviewCard } from "./ImportPreviewCard";

/**
 * 문제 묶음을 올린다 (#538).
 *
 * ## 미리보기가 먼저다
 *
 * 파일을 고르자마자 만들어 버리면 잘못 만든 묶음이 **문제 번호를 하나 먹고** 지워야 할
 * 것으로 남는다 — 번호는 사용자에게 보이는 값이다 (#204). 그래서 고르면 미리보기만
 * 부르고, 저장은 사람이 한 번 더 누른다.
 *
 * ## 파일 입력을 `Field` 로 감싸지 않는다
 *
 * `Field` 는 children 을 `<label>` 로 감싼다. 파일 입력을 감싸면 **라벨 아무 데나 눌러도
 * 파일 창이 뜬다** — #306 이 같은 자리에서 났다. 아바타 편집기(#116)가 쓰는 방식대로
 * 숨긴 입력을 버튼이 부른다.
 */
export function AdminProblemImportPage() {
  const router = useRouter();
  const input = useRef<HTMLInputElement>(null);

  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ProblemBundlePreview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const pick = async (picked: File | undefined) => {
    if (!picked) return;
    setFile(picked);
    setPreview(null);
    setError(null);
    setBusy(true);
    try {
      setPreview(await problemApi.importPreview(picked));
    } catch (caught) {
      // 서버가 어디가 틀렸는지 적어 보낸다. "잘못된 파일입니다" 로는 고칠 수 없다.
      setError(caught instanceof ApiError ? caught.message : "파일을 읽지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const save = async () => {
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      // 미리보기가 아니라 **파일을 다시 올린다** — 서버가 들고 있지 않기 때문이다.
      const { created } = await problemApi.importBundle(file);
      /*
        초안이라 사용자 화면에는 없다. 고칠 수 있는 곳으로 보낸다.

        **여럿이면 편집 화면이 아니라 목록으로 보낸다** (#623) — 하나를 골라 열면
        나머지가 들어갔는지 확인할 자리가 없다.
      */
      router.push(created.length === 1 ? `/admin/problems/${created[0].id}/edit` : "/admin/problems");
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "문제를 만들지 못했습니다.");
      setBusy(false);
    }
  };

  const blocked = preview !== null && preview.problems.some(isBlocked);

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-ink">문제 묶음 올리기</h1>

      <Card className="p-5">
        <CardTitle>파일</CardTitle>
        <p className="mt-1 text-sm text-ink-muted">
          <code>problem.json</code> 하나를 그대로 올리거나, 테스트케이스가 많으면{" "}
          <code>problem.json</code> 과 <code>testcases/</code> 를 담은 zip 을 올립니다.
          형식은 <code>scripts/seed-problems</code> 의 파일과 같습니다.
          <br />
          <strong>문제를 여러 개 담아도 됩니다</strong> — zip 안에 문제마다 폴더를 하나씩
          두면 한 번에 만듭니다 (#623). 하나라도 걸리면 아무것도 만들지 않습니다.
        </p>

        <div className="mt-3 flex flex-wrap items-center gap-2">
          <Button type="button" onClick={() => input.current?.click()} disabled={busy}>
            파일 고르기
          </Button>
          {file ? <span className="text-sm text-ink-muted">{file.name}</span> : null}
        </div>

        <input
          ref={input}
          type="file"
          accept=".json,.zip,application/json,application/zip"
          className="hidden"
          onChange={(event) => pick(event.target.files?.[0])}
        />
      </Card>

      {busy && !preview ? <p className="text-sm text-ink-muted">읽는 중…</p> : null}
      {error ? <Alert>{error}</Alert> : null}

      {preview ? (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <Badge>{preview.source === "ZIP" ? "zip 묶음" : "JSON 파일"}</Badge>
            <span className="text-sm text-ink-muted">문제 {preview.problems.length}개</span>
          </div>
          {preview.problems.map((each) => (
            <ImportPreviewCard key={each.slug} preview={each} />
          ))}
          <div className="flex flex-wrap items-center gap-2">
            <Button type="button" onClick={save} disabled={busy || blocked}>
              {preview.problems.length === 1 ? "이대로 만들기" : `${preview.problems.length}개 모두 만들기`}
            </Button>
            {blocked ? (
              <span className="text-sm text-ink-muted">
                위의 문제를 고쳐서 다시 올려야 만들 수 있습니다.
              </span>
            ) : (
              <span className="text-sm text-ink-muted">
                초안으로 만들어집니다. 공개는 편집 화면에서 합니다.
              </span>
            )}
          </div>
        </>
      ) : null}
    </div>
  );
}
