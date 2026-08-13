"use client";

import type { Runtime } from "@/entities/problem";
import { useCallback, useEffect, useMemo, useState } from "react";
import { fileDraftKey, initialSources, readFileDraft } from "./draft";

/**
 * 파일이 여럿인 문제의 편집 상태 (#457, #498).
 *
 * **화면에서 떼어 낸 이유는 #383 과 같다.** 무엇을 편집 중인지와 그것을 어디에 저장하는지가
 * 렌더 안에 섞이면, "제출한 코드가 자기가 쓴 것이 아니었다" 는 사고를 시험으로 막을 수 없다.
 *
 * 규칙은 하나뿐이다: **사용자가 언어를 바꿀 때만** 내용이 다시 채워진다.
 */
/**
 * @param openFile 처음에 열어 둘 파일. 컴파일 오류에서 돌아온 링크가 이것을 준다 (#498) —
 *   "Helper.java 17번 줄" 을 눌렀는데 `Main.java` 가 열리면 한 번 더 찾아야 한다.
 */
export function useFileSources(
  slug: string,
  runtime: Runtime | undefined,
  openFile?: string | null,
) {
  const files = useMemo(() => runtime?.files ?? [], [runtime]);
  const runtimeId = runtime?.id ?? "";

  const [sources, setSources] = useState<Record<string, string>>(() =>
    initialSources(files, (name) => readFileDraft(slug, runtimeId, name)),
  );
  const [active, setActive] = useState(
    () =>
      files.find((file) => file.name === openFile)?.name ??
      files[0]?.name ??
      "",
  );

  /** 언어가 바뀌면 그 언어의 파일로 갈아 끼운다. 그 외에는 건드리지 않는다. */
  const reset = useCallback(
    (next: Runtime | undefined) => {
      const nextFiles = next?.files ?? [];
      setSources(
        initialSources(nextFiles, (name) =>
          readFileDraft(slug, next?.id ?? "", name),
        ),
      );
      setActive(nextFiles[0]?.name ?? "");
    },
    [slug],
  );

  useEffect(() => {
    if (!runtimeId) return;
    // **빈 값도 저장한다** (#383). 지운 것도 사용자가 한 일이다.
    for (const [name, code] of Object.entries(sources)) {
      localStorage.setItem(fileDraftKey(slug, runtimeId, name), code);
    }
  }, [slug, runtimeId, sources]);

  const update = useCallback(
    (name: string, code: string) =>
      setSources((previous) => ({ ...previous, [name]: code })),
    [],
  );

  /**
   * 제출에 실을 파일들. **고칠 수 없는 파일은 보내지 않는다** — 서버가 문제의 것을 쓴다.
   *
   * 보내도 서버가 무시하지만, 보내지 않는 편이 "무엇이 내 것인가" 를 흐리지 않는다.
   */
  const payload = useMemo(
    () =>
      files
        .filter((file) => file.editable)
        .map((file) => ({
          name: file.name,
          sourceCode: sources[file.name] ?? "",
        })),
    [files, sources],
  );

  return { files, sources, active, setActive, update, reset, payload };
}
