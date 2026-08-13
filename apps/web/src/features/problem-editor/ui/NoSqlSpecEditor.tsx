"use client";

import type { NoSqlSpec } from "@/entities/problem";
import { CheckboxField, Field, Textarea } from "@/shared/ui";

/**
 * NoSQL 문제의 시드·정답·확인 명령 (#455).
 *
 * **정답을 결과가 아니라 상태로 받는다.** 제출이 명령의 연속이면 마지막 명령의 출력은
 * 문제가 묻는 것의 일부일 뿐이다 — `LPUSH` 가 돌려주는 길이는 정답과 상관이 없다.
 */
export function NoSqlSpecEditor({
  value,
  onChange,
}: {
  value: NoSqlSpec;
  onChange: (next: NoSqlSpec) => void;
}) {
  const update = <K extends keyof NoSqlSpec>(key: K, next: NoSqlSpec[K]) =>
    onChange({ ...value, [key]: next });

  return (
    <div className="space-y-4">
      <p className="text-xs text-ink-muted">
        문제마다 새 Redis 가 두 개 뜹니다 — 정답을 돌릴 것과 제출을 돌릴 것.
        제출은 데이터 명령만 쓸 수 있고 <code>CONFIG</code>·<code>EVAL</code>·
        <code>FLUSHALL</code> 같은 것은 막혀 있습니다.
      </p>

      <Field label="시드 명령 (선택)">
        <Textarea
          rows={6}
          className="font-mono text-xs"
          value={value.seedCommands ?? ""}
          onChange={(event) =>
            update("seedCommands", event.target.value || null)
          }
          placeholder={"ZADD scores 10 kim\nZADD scores 30 lee"}
        />
      </Field>

      <Field label="정답 명령">
        <Textarea
          rows={5}
          className="font-mono text-xs"
          value={value.answerCommands}
          onChange={(event) => update("answerCommands", event.target.value)}
          placeholder="ZINCRBY scores 5 kim"
          required
        />
      </Field>

      <Field label="끝난 뒤의 상태를 읽는 명령">
        <Textarea
          rows={4}
          className="font-mono text-xs"
          value={value.verifyCommands}
          onChange={(event) => update("verifyCommands", event.target.value)}
          placeholder="ZRANGE scores 0 -1 WITHSCORES"
          required
        />
      </Field>
      <p className="text-xs text-ink-muted">
        {/* 명령의 연속에는 견줄 결과 집합이 없다 — 이것이 없으면 무엇을 정답으로 볼지가 없다. */}
        정답 명령을 돌린 쪽과 제출을 돌린 쪽에서 <b>각각 이 명령</b>을 돌려 그
        출력을 견줍니다. SQL 과 달리 <b>비워 둘 수 없습니다</b> — 명령의
        연속에는 견줄 결과 집합이 없기 때문입니다.
      </p>

      <CheckboxField
        className="text-xs"
        checked={value.ignoreOrder}
        onCheckedChange={(next) => update("ignoreOrder", next)}
        label={
          <>
            줄 순서를 무시하고 비교합니다.
            <span className="block font-normal text-ink-muted">
              {/* SQL 의 행 순서와 반대다. 정렬 집합·리스트에서 순서는 자료의 일부다. */}
              기본은 <b>순서를 지킵니다</b> — 정렬 집합·리스트에서 순서는 자료의
              일부라, 무시하면 정렬이 틀린 답도 통과합니다. 집합(SET)처럼 순서가
              없는 자료만 켜세요.
            </span>
          </>
        }
      />
    </div>
  );
}
