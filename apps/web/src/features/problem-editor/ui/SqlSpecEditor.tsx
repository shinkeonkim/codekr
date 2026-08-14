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
          문제마다 새 PostgreSQL 이 뜨고, 제출 쿼리는 기본적으로 읽기 전용 권한으로 실행됩니다.
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

      <Field label="끝난 뒤의 상태를 읽는 쿼리 (선택)">
        <Textarea
          rows={4}
          className="font-mono text-xs"
          value={value.verifySql ?? ""}
          onChange={(event) => update("verifySql", event.target.value || null)}
          placeholder="SELECT id, city FROM members ORDER BY id;"
        />
      </Field>
      <p className="text-xs text-ink-muted">
        {/* INSERT·UPDATE·CREATE TABLE 은 결과 집합이 없다 — 바뀌는 것은 DB 의 상태다. */}
        비워 두면 제출 쿼리의 <b>결과 집합</b>을 견줍니다. 채우면 정답 스크립트를 돌린 DB 와
        제출을 돌린 DB 에서 각각 이 쿼리를 돌려 <b>끝난 뒤의 상태</b>를 견줍니다 —
        <code>INSERT</code>·<code>UPDATE</code>·<code>CREATE TABLE</code> 문제에 필요합니다.
      </p>

      <CheckboxField
        className="text-xs"
        checked={value.allowWrite}
        onCheckedChange={(next) => update("allowWrite", next)}
        label={
          <>
            제출이 데이터를 바꿀 수 있습니다.
            <span className="block font-normal text-ink-muted">
              {/* 문자열 필터가 아니라 권한으로 연다 — 필터는 우회되지만 권한은 아니다. */}
              켜면 제출 롤에 쓰기 권한을 줍니다. 위의 상태를 읽는 쿼리가 있어야 켤 수 있습니다 —
              없으면 채점이 조용히 결과 집합 비교로 돌아가 아무 답이나 통과합니다.
            </span>
          </>
        }
      />

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
