"use client";

import { adminAffiliationApi } from "@/entities/affiliation";
import type { Affiliation, AffiliationKind } from "@/entities/affiliation";
import { ApiError } from "@/shared/api";
import { Button, Card, Field, Input, useToast } from "@/shared/ui";
import { useEffect, useState } from "react";
import { AffiliationCard } from "./AffiliationCard";

/**
 * 소속과 도메인 관리 (#428, #397 화면).
 *
 * **목록이 있어야 인증이 붙는다.** 사용자가 학교 메일을 확인하면(#396) 그 도메인이
 * 어느 소속인지 여기서 찾는다 — 여기가 비어 있으면 아무도 소속을 얻지 못한다.
 *
 * **자동으로 만들지 않는 이유**는 기획서에 있다. `@gmail.com` 이 "지메일 대학" 이 되고
 * 오타 도메인이 소속으로 쌓인다. 대신 손으로 넣는 이 화면이 있어야 한다.
 */
export function AdminAffiliationsPage() {
  const toast = useToast();
  const [items, setItems] = useState<Affiliation[] | null>(null);
  const [name, setName] = useState("");
  const [kind, setKind] = useState<AffiliationKind>("SCHOOL");

  const reload = () =>
    adminAffiliationApi
      .list()
      .then(setItems)
      .catch(() => setItems([]));

  useEffect(() => {
    reload();
  }, []);

  const fail = (caught: unknown, fallback: string) =>
    toast.error(caught instanceof ApiError ? caught.message : fallback);

  const create = async () => {
    if (!name.trim()) {
      toast.error("이름을 입력해 주세요.");
      return;
    }
    try {
      await adminAffiliationApi.create({ name: name.trim(), kind });
      setName("");
      toast.success("만들었습니다. 이제 도메인을 붙여 주세요.");
      await reload();
    } catch (caught) {
      fail(caught, "만들지 못했습니다.");
    }
  };

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold text-ink">소속 관리</h1>
        <p className="mt-1 text-sm text-ink-muted">
          학교·회사와 그 메일 도메인입니다. 여기 등록된 도메인의 메일을 확인한 사람만 그
          소속을 붙일 수 있습니다.
        </p>
      </div>

      <Card className="space-y-3">
        <div className="grid gap-3 sm:grid-cols-[1fr_10rem_auto] sm:items-end">
          <Field label="이름">
            <Input
              placeholder="예: 서울대학교"
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </Field>
          <Field label="종류">
            {/* 둘뿐이라 고르는 것보다 눌러 두는 편이 빠르다. */}
            <div className="flex gap-2">
              {(["SCHOOL", "COMPANY"] as const).map((each) => (
                <Button
                  key={each}
                  variant={kind === each ? "primary" : "secondary"}
                  className="px-3 py-1 text-xs"
                  onClick={() => setKind(each)}
                >
                  {each === "SCHOOL" ? "학교" : "회사"}
                </Button>
              ))}
            </div>
          </Field>
          <Button onClick={create}>만들기</Button>
        </div>
        {/* 도메인 없이 만든 소속은 아무에게도 붙지 않는다. 만들자마자 그렇게 안내한다. */}
        <p className="text-xs text-ink-muted">
          만든 뒤 도메인을 붙여야 합니다. 도메인이 없는 소속은 아무에게도 붙지 않습니다.
        </p>
      </Card>

      {items?.length === 0 ? (
        <p className="text-sm text-ink-muted">아직 등록한 소속이 없습니다.</p>
      ) : null}

      <div className="space-y-3">
        {items?.map((item) => (
          <AffiliationCard key={item.id} affiliation={item} onChanged={reload} onFail={fail} />
        ))}
      </div>
    </div>
  );
}
