"use client";

import type { GitSpec } from "@/entities/problem";
import { Field, Textarea } from "@/shared/ui";

/**
 * Git 문제의 시드·정답·확인 명령 (#654).
 *
 * **Redis(#455)와 모양이 같다.** 담기는 것이 git 명령이라는 것만 다르다.
 */
export function GitSpecEditor({
  value,
  onChange,
}: {
  value: GitSpec;
  onChange: (next: GitSpec) => void;
}) {
  const update = <K extends keyof GitSpec>(key: K, next: GitSpec[K]) =>
    onChange({ ...value, [key]: next });

  // 커밋 해시를 그대로 찍으면 **같은 결과에 이른 다른 풀이가 틀린 답**이 된다.
  const usesCommitHash = /%H|%h\b/.test(value.verifyCommands);

  return (
    <div className="space-y-4">
      <p className="text-xs text-ink-muted">
        문제마다 새 저장소가 둘 만들어집니다 — 정답을 돌릴 것과 제출을 돌릴 것.
        <b>한 줄에 명령 하나</b>이고 <b>git 명령만</b> 쓸 수 있습니다. 네트워크 명령
        (<code>clone</code>·<code>fetch</code>·<code>push</code>)은 즉시 거부됩니다.
      </p>
      <p className="text-xs text-ink-muted">
        작성자·커미터의 이름과 시각은 <b>고정됩니다.</b> 고정하지 않으면 커밋 해시가
        매번 달라져 같은 답이 때에 따라 틀립니다.
      </p>

      <Field label="시드 명령 (선택)">
        <Textarea
          rows={5}
          className="font-mono text-xs"
          value={value.seedCommands ?? ""}
          onChange={(event) => update("seedCommands", event.target.value || null)}
          placeholder={"git commit -q --allow-empty -m base\ngit commit -q --allow-empty -m oops"}
        />
      </Field>

      <Field label="정답 명령">
        <Textarea
          rows={4}
          className="font-mono text-xs"
          value={value.answerCommands}
          onChange={(event) => update("answerCommands", event.target.value)}
          placeholder="git reset -q --hard HEAD~1"
          required
        />
      </Field>

      <Field label="끝난 뒤를 읽는 명령">
        <div className="space-y-2">
          <Textarea
            rows={3}
            className="font-mono text-xs"
            value={value.verifyCommands}
            onChange={(event) => update("verifyCommands", event.target.value)}
            placeholder="git log --format='%T %s'"
            required
          />
          {usesCommitHash ? (
            <p className="text-xs text-danger">
              <b>커밋 해시(<code>%H</code>·<code>%h</code>)로 견주면 위험합니다.</b> 메시지 한
              글자만 달라도 해시가 달라져, <b>같은 결과에 이른 다른 풀이가 틀린 답</b>이
              됩니다. 트리 해시(<code>%T</code>)와 그래프 모양은 내용만 봅니다.
            </p>
          ) : null}
        </div>
      </Field>
    </div>
  );
}
