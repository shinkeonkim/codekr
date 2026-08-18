"use client";

import { request } from "@/shared/api";
import { Field, Select } from "@/shared/ui";
import { useEffect, useState } from "react";

interface RuntimeLanguage {
  id: string;
  label: string;
  runtimes: { id: string; label: string }[];
}

/**
 * 언어로 크게 고르고, 원하면 버전까지 좁힌다 (#618).
 *
 * **버전 칸은 언어를 고른 뒤에만 뜬다.** 런타임이 스물둘이라 한 줄에 늘어놓으면 고르는
 * 일 자체가 일이 된다. 대부분은 "파이썬으로 풀 수 있는 문제" 를 찾지 `python:3.12` 를
 * 찾지 않는다.
 *
 * 그래도 버전이 필요한 자리가 있다 — 문제가 특정 버전만 허용할 수 있고(#419),
 * `PostgreSQL` 과 `MariaDB` 는 같은 "SQL" 이지만 문법이 다르다 (#454).
 */
export function LanguageFilter({
  language,
  runtime,
  onChange,
}: {
  language: string;
  runtime: string;
  onChange: (next: { language: string; runtime: string }) => void;
}) {
  const [languages, setLanguages] = useState<RuntimeLanguage[] | null>(null);

  useEffect(() => {
    let alive = true;
    request<RuntimeLanguage[]>("/api/v1/runtimes/languages")
      .then((next) => alive && setLanguages(next))
      .catch(() => alive && setLanguages([]));
    return () => {
      alive = false;
    };
  }, []);

  // 목록을 못 받았으면 칸을 그리지 않는다 — 고를 것이 없는 선택기는 고장으로 보인다.
  if (!languages || languages.length === 0) return null;

  const selected = languages.find((it) => it.id === language);

  return (
    <>
      <Field label="언어">
        <Select
          value={language}
          onChange={(event) =>
            // **언어를 바꾸면 버전을 버린다.** 남겨 두면 파이썬을 고른 채 `sql:mariadb11`
            // 이 걸려 빈 목록이 나오고, 화면에는 그 이유가 보이지 않는다.
            onChange({ language: event.target.value, runtime: "" })
          }
        >
          <option value="">전체</option>
          {languages.map((option) => (
            <option key={option.id} value={option.id}>
              {option.label}
            </option>
          ))}
        </Select>
      </Field>

      {/* 고를 것이 하나뿐이면 버전 칸을 그리지 않는다 — 고르는 뜻이 없다. */}
      {selected && selected.runtimes.length > 1 ? (
        <Field label={`${selected.label} 버전`}>
          <Select
            value={runtime}
            onChange={(event) => onChange({ language, runtime: event.target.value })}
          >
            <option value="">전체</option>
            {selected.runtimes.map((option) => (
              <option key={option.id} value={option.id}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>
      ) : null}
    </>
  );
}
