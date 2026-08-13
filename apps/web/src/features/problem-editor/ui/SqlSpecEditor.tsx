"use client";

import type { SqlSpec } from "@/entities/problem";
import { CheckboxField, Field, Textarea } from "@/shared/ui";

/**
 * SQL 문제의 스키마와 정답 쿼리 (#60).
 *
 * **정답을 결과 집합이 아니라 쿼리로 받는다.** 시드 데이터를 고칠 때 기대 결과를
 * 손으로 같이 고쳐야 한다면, 고치는 것을 잊는 순간 모든 제출이 틀리게 된다.
 */
export function SqlSpecEditor({
  value,
  onChange,
}: {
  value: SqlSpec;
  onChange: (next: SqlSpec) => void;
}) {
  const update = <K extends keyof SqlSpec>(key: K, next: SqlSpec[K]) =>
    onChange({ ...value, [key]: next });

  return (
    <div className="space-y-4">
      <div>
                <p className="mt-1 text-xs text-ink-muted">
          문제마다 새 PostgreSQL 이 뜨고, 제출 쿼리는 읽기 전용 권한으로 실행됩니다.
        </p>
      </div>

      <Field label="스키마와 시드 데이터">
        <Textarea
          rows={10}
          className="font-mono text-xs"
          value={value.schemaSql}
          onChange={(event) => update("schemaSql", event.target.value)}
          placeholder={"CREATE TABLE members (id int, city text);\nINSERT INTO members VALUES (1, '서울');"}
          required
        />
      </Field>

      <Field label="정답 쿼리">
        <Textarea
          rows={5}
          className="font-mono text-xs"
          value={value.answerSql}
          onChange={(event) => update("answerSql", event.target.value)}
          placeholder="SELECT city, count(*) FROM members GROUP BY city ORDER BY city;"
          required
        />
      </Field>
      <p className="text-xs text-ink-muted">
        채점할 때 이 쿼리를 먼저 돌려 기대 결과를 만듭니다. 시드를 고치면 기대 결과도 따라갑니다.
      </p>

      <CheckboxField
        className="text-xs"
        checked={value.ignoreRowOrder}
        onCheckedChange={(next) => update("ignoreRowOrder", next)}
        label={
          <>
            행 순서를 무시하고 비교합니다.
            {/* 정렬이 문제의 일부인 경우에만 끈다. 켜 두는 것이 기본이다. */}
            <span className="block font-normal text-ink-muted">
              정렬이 문제의 일부라면 끄세요. 켠 채로 두는 것이 기본입니다 — 문제가 정렬을
              요구하지 않는데 순서를 비교하면 맞는 답이 틀린 것으로 나옵니다.
            </span>
          </>
        }
      />
    </div>
  );
}
