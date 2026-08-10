"use client";

import type { ProblemDetail } from "@/entities/problem";
import { VISIBILITY_DESCRIPTIONS, VISIBILITY_LABELS, submissionApi } from "@/entities/submission";
import type { RunResult, SubmissionVisibility } from "@/entities/submission";
import { useAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { CodeEditor } from "@/shared/ui";
import { Alert, Button, Card, Select, Textarea } from "@/shared/ui";

/** 작성 중인 코드를 문제·언어별로 브라우저에 남겨, 새로고침해도 잃지 않게 한다. */
const draftKey = (slug: string, runtimeId: string) => `codekr.draft.${slug}.${runtimeId}`;

/** 서버 렌더링 중에는 저장소가 없으므로 초안이 없는 것으로 본다. */
function readDraft(slug: string, runtimeId: string): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(draftKey(slug, runtimeId));
}

export function SolveWorkspace({ problem }: { problem: ProblemDetail }) {
  const router = useRouter();
  const { user } = useAuth();
  const [runtimeId, setRuntimeId] = useState(problem.runtimes[0]?.id ?? "");
  const [source, setSource] = useState("");
  const [stdin, setStdin] = useState(problem.examples[0]?.input ?? "");
  const [runResult, setRunResult] = useState<RunResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<"run" | "submit" | null>(null);
  const [visibility, setVisibility] = useState<SubmissionVisibility>("PRIVATE");

  const runtime = useMemo(
    () => problem.runtimes.find((it) => it.id === runtimeId) ?? problem.runtimes[0],
    [problem.runtimes, runtimeId],
  );

  // 언어가 바뀌면 저장해 둔 초안을 불러오고, 없으면 템플릿에서 시작한다.
  // 렌더 중 상태를 맞추는 방식이라 이펙트로 인한 추가 렌더가 생기지 않는다.
  const [loadedRuntimeId, setLoadedRuntimeId] = useState<string | null>(null);
  if (runtime && loadedRuntimeId !== runtime.id) {
    setLoadedRuntimeId(runtime.id);
    setSource(readDraft(problem.slug, runtime.id) ?? runtime.template);
  }

  useEffect(() => {
    if (!runtime || !source) return;
    localStorage.setItem(draftKey(problem.slug, runtime.id), source);
  }, [problem.slug, runtime, source]);

  const guardLogin = () => {
    if (user) return true;
    setError("로그인이 필요합니다.");
    return false;
  };

  const handleRun = async () => {
    if (!runtime || !guardLogin() || busy !== null) return;
    setBusy("run");
    setError(null);
    setRunResult(null);
    try {
      setRunResult(await submissionApi.run(problem.slug, { runtimeId: runtime.id, sourceCode: source, stdin }));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "실행에 실패했습니다.");
    } finally {
      setBusy(null);
    }
  };

  /**
   * 제출하고 그 제출의 상세 화면으로 옮긴다 (#80).
   *
   * 화면에 남아 있으면 접수됐는지 알기 어렵고, 반응이 없어 보이면 한 번 더 누르게 된다.
   * 그리고 **성공했을 때는 busy 를 풀지 않는다** — 응답이 온 뒤 이동이 끝나기 전 사이에
   * 다시 눌릴 수 있기 때문이다. 이동하면 컴포넌트가 사라지므로 그대로 두는 편이 맞다.
   */
  const handleSubmit = async () => {
    if (!runtime || !guardLogin() || busy !== null) return;
    setBusy("submit");
    setError(null);
    setRunResult(null);
    try {
      const { submissionId } = await submissionApi.submit(problem.slug, {
        runtimeId: runtime.id,
        sourceCode: source,
        visibility,
      });
      router.push(`/submissions/${submissionId}`);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "제출에 실패했습니다.");
      setBusy(null);
    }
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        <Select
          className="min-w-0 flex-1"
          value={runtime?.id ?? ""}
          onChange={(event) => setRuntimeId(event.target.value)}
        >
          {problem.runtimes.map((item) => (
            <option key={item.id} value={item.id}>
              {item.label}
            </option>
          ))}
        </Select>
        <Button variant="secondary" onClick={handleRun} disabled={busy !== null}>
          {busy === "run" ? "실행 중…" : "실행"}
        </Button>
        <Button onClick={handleSubmit} disabled={busy !== null}>
          {busy === "submit" ? "제출 중…" : "제출"}
        </Button>
      </div>

      {error ? <Alert>{error}</Alert> : null}

      <CodeEditor
        language={runtime?.monacoLanguage ?? "plaintext"}
        value={source}
        onChange={setSource}
      />

      <Card className="space-y-2 p-4">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="text-sm font-semibold text-ink">소스 코드 공개 범위</h3>
          <Select
            className="ml-auto w-56"
            value={visibility}
            onChange={(event) => setVisibility(event.target.value as SubmissionVisibility)}
          >
            {Object.entries(VISIBILITY_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </div>
        <p className="text-xs text-ink-muted">{VISIBILITY_DESCRIPTIONS[visibility]}</p>
      </Card>

      <Card className="space-y-2 p-4">
        <h3 className="text-sm font-semibold text-ink">입력 (실행에만 사용)</h3>
        <Textarea rows={3} value={stdin} onChange={(event) => setStdin(event.target.value)} />
        {runResult ? <RunResultView result={runResult} /> : null}
      </Card>
    </div>
  );
}

function RunResultView({ result }: { result: RunResult }) {
  return (
    <div className="space-y-2 pt-2">
      <div className="flex items-center gap-2 text-xs text-ink-muted">
        <span>상태 {result.status}</span>
        <span>· {result.runtimeMs}ms</span>
        {result.truncated ? <span className="text-warn">· 출력이 잘렸습니다</span> : null}
      </div>
      <OutputBlock title="표준 출력" body={result.stdout} />
      {result.stderr ? <OutputBlock title="표준 에러" body={result.stderr} tone="danger" /> : null}
    </div>
  );
}

function OutputBlock({ title, body, tone }: { title: string; body: string; tone?: "danger" }) {
  return (
    <div>
      <p className="mb-1 text-xs font-medium text-ink-muted">{title}</p>
      <pre
        className={`max-h-40 overflow-auto rounded-lg bg-surface-muted p-3 text-xs ${
          tone === "danger" ? "text-danger" : "text-ink"
        }`}
      >
        {body || "(없음)"}
      </pre>
    </div>
  );
}
