"use client";

import type { MongoSpec } from "@/entities/problem";
import { CheckboxField, Field, Textarea } from "@/shared/ui";

/**
 * MongoDB 문제의 시드·정답·확인 스크립트 (#527).
 *
 * **모양은 Redis(#455)와 같고 담기는 것이 다르다** — 여기는 `mongosh` 스크립트다.
 *
 * **"결과 집합" 칸을 따로 두지 않는다.** 확인 스크립트가 `find` 를 찍으면 결과 집합이
 * 되고 컬렉션을 세면 상태가 된다 — 한 칸으로 둘 다 된다.
 */
export function MongoSpecEditor({
  value,
  onChange,
}: {
  value: MongoSpec;
  onChange: (next: MongoSpec) => void;
}) {
  const update = <K extends keyof MongoSpec>(key: K, next: MongoSpec[K]) =>
    onChange({ ...value, [key]: next });

  return (
    <div className="space-y-4">
      <p className="text-xs text-ink-muted">
        문제마다 새 MongoDB 가 뜨고 데이터베이스 둘로 나뉩니다 — 정답을 돌릴 것과 제출을
        돌릴 것. <code>$where</code>·<code>mapReduce</code>·<code>eval</code> 처럼{" "}
        <b>서버 안에서 코드를 돌리는 것</b>은 막혀 있습니다. 시간 제한을 우회하는 길이기
        때문입니다.
      </p>

      <Field label="시드 스크립트 (선택)">
        <Textarea
          rows={6}
          className="font-mono text-xs"
          value={value.seedScript ?? ""}
          onChange={(event) => update("seedScript", event.target.value || null)}
          placeholder={'db.stocks.insertMany([\n  { sku: "A-1", qty: 3 },\n  { sku: "B-2", qty: 0 },\n]);'}
        />
      </Field>

      <Field label="정답 스크립트">
        <Textarea
          rows={5}
          className="font-mono text-xs"
          value={value.answerScript}
          onChange={(event) => update("answerScript", event.target.value)}
          placeholder='db.stocks.updateOne({ sku: "A-1" }, { $inc: { qty: 5 } });'
          required
        />
      </Field>

      <Field label="끝난 뒤를 읽는 스크립트">
        <Textarea
          rows={4}
          className="font-mono text-xs"
          value={value.verifyScript}
          onChange={(event) => update("verifyScript", event.target.value)}
          placeholder={'db.stocks.find({}, { _id: 0 }).sort({ sku: 1 }).forEach(printjson);'}
          required
        />
      </Field>
      <p className="text-xs text-ink-muted">
        이 스크립트가 <b>정답과 제출 양쪽에서 똑같이</b> 돌고, 그 출력을 견줍니다.
        선택이 아닙니다 — 이것이 없으면 무엇을 정답으로 볼지가 없습니다.
        <code>_id</code> 는 실행할 때마다 달라지므로 <b>빼고 찍으십시오</b>.
      </p>

      <CheckboxField
        className="text-xs"
        checked={value.ignoreOrder}
        onCheckedChange={(next) => update("ignoreOrder", next)}
        label={
          <>
            줄 순서를 무시하고 비교합니다.
            <span className="block font-normal text-ink-muted">
              기본은 <b>순서를 지킵니다</b>. 확인 스크립트에서 <code>sort()</code> 를
              쓰면 이것을 켤 필요가 없고, 그 편이 무엇을 재는지 분명합니다.
            </span>
          </>
        }
      />
    </div>
  );
}
