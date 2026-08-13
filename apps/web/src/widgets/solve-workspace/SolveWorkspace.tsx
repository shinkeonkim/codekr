"use client";

import type { ProblemDetail, Runtime } from "@/entities/problem";
import { VISIBILITY_DESCRIPTIONS, VISIBILITY_LABELS, submissionApi } from "@/entities/submission";
import type { RunResult, SubmissionVisibility } from "@/entities/submission";
import { userApi } from "@/entities/user";
import { useAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { CodeEditor } from "@/shared/ui";
import { draftKey, initialSource, readDraft } from "./draft";
import { Alert, Button, Card, Select, Textarea } from "@/shared/ui";

interface Props {
  problem: ProblemDetail;
  /** 고른 실행 환경을 바깥에 알린다. 머리말이 그 언어의 제한을 보여주기 위함이다 (#97). */
  onRuntimeChange?: (runtime: Runtime) => void;
}

export function SolveWorkspace({ problem, onRuntimeChange }: Props) {
  const router = useRouter();
  const { user } = useAuth();
  const [runtimeId, setRuntimeId] = useState(problem.runtimes[0]?.id ?? "");
  /*
    **처음 한 번만 정한다** (#383).

    전에는 렌더 중에 `loadedRuntimeId !== runtime.id` 를 보고 `setSource(템플릿)` 을
    했다. `runtime` 은 `problem.runtimes` 에서 다시 계산되는 값이라, 그 계산이 한 번
    더 돌면서 id 가 흔들리면 **이미 친 코드가 템플릿으로 덮인다.** 그리고 그 덮인 것이
    그대로 제출돼 채점되고, 정답률·랭킹·스트릭이 그 위에 쌓인다.

    지금은 **사용자가 언어를 바꿀 때만** 코드가 바뀐다. 그 외에 `source` 를 건드리는
    길이 없으므로, 무엇이 몇 번 다시 계산되든 친 코드는 그대로 있다.
  */
  const [source, setSource] = useState(() =>
    initialSource(
      readDraft(problem.slug, problem.runtimes[0]?.id ?? ""),
      problem.runtimes[0]?.template ?? "",
    ),
  );
  const [stdin, setStdin] = useState(problem.examples[0]?.input ?? "");
  const [runResult, setRunResult] = useState<RunResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<"run" | "submit" | null>(null);
  // 사용자 기본값에서 시작한다 (#104). 불러오기 전에는 가장 좁은 범위를 보여준다 —
  // 잠깐이라도 실제보다 넓은 범위가 보이면 그걸 믿고 제출하게 된다.
  const [visibility, setVisibility] = useState<SubmissionVisibility>("PRIVATE");

  const runtime = useMemo(
    () => problem.runtimes.find((it) => it.id === runtimeId) ?? problem.runtimes[0],
    [problem.runtimes, runtimeId],
  );

  useEffect(() => {
    if (runtime) onRuntimeChange?.(runtime);
  }, [runtime, onRuntimeChange]);

  useEffect(() => {
    if (!user) return;
    userApi
      .settings()
      .then((settings) => setVisibility(settings.defaultSubmissionVisibility))
      .catch(() => undefined);
  }, [user]);

  /**
   * 언어를 바꾼다 (#383).
   *
   * **코드가 바뀌는 유일한 자리다.** 사용자가 고른 것이므로 덮어써도 되고, 그 언어의
   * 초안이 있으면 그것을, 없으면 템플릿을 준다.
   */
  const changeRuntime = (nextId: string) => {
    setRuntimeId(nextId);
    const next = problem.runtimes.find((it) => it.id === nextId);
    if (next) setSource(initialSource(readDraft(problem.slug, next.id), next.template));
  };

  useEffect(() => {
    if (!runtime) return;
    // **빈 값도 저장한다.** 전에는 건너뛰어서, 코드를 전부 지우고 나가면 다시 들어왔을
    // 때 옛 초안이 되살아났다 — 지운 것도 사용자가 한 일이다.
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
      {/*
        **목록이 짧으면 사용자는 고장으로 읽는다** (#419). 출제자가 언어를 좁혀 둔
        것이면 그렇게 말한다 — 그러지 않으면 "내 언어가 왜 없지" 로 남는다.
      */}
      {problem.runtimeRestricted ? (
        <p className="text-xs text-ink-muted">
          이 문제는 아래 언어로만 풀 수 있습니다. 출제자가 정한 것입니다.
        </p>
      ) : null}

      <div className="flex items-center gap-2">
        <Select
          className="min-w-0 flex-1"
          value={runtime?.id ?? ""}
          onChange={(event) => changeRuntime(event.target.value)}
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
