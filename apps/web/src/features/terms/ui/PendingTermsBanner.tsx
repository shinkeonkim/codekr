"use client";

import { userApi } from "@/entities/user";
import type { TermSummary } from "@/entities/user";
import { useAuth } from "@/features/auth";
import { Alert, Button, useToast } from "@/shared/ui";
import Link from "next/link";
import { useEffect, useState } from "react";

/**
 * 개정된 약관을 다시 받는다 (#235).
 *
 * **막지 않고 알린다** (②). 로그인하자마자 막으면 오타 하나를 고친 개정에도 모두가
 * 멈추고, 그러면 개정 자체를 안 하게 된다. 다시 받아야 하는 개정에만 이 배너가 뜬다
 * (`reconsent`) — 그 판단은 사람이 한다.
 */
export function PendingTermsBanner() {
  const { user } = useAuth();
  const toast = useToast();
  const [pending, setPending] = useState<TermSummary[]>([]);

  useEffect(() => {
    if (!user) return;
    userApi
      .pendingTerms()
      .then(setPending)
      .catch(() => setPending([]));
  }, [user]);

  if (pending.length === 0) return null;

  const agree = async () => {
    try {
      await userApi.agreeTerms(pending.map((term) => term.id));
      setPending([]);
      toast.success("약관에 동의했습니다.");
    } catch {
      toast.error("동의하지 못했습니다.");
    }
  };

  return (
    <Alert tone="warn">
      <div className="flex flex-wrap items-center gap-2">
        <span>
          약관이 개정됐습니다 —{" "}
          {pending.map((term, index) => (
            <span key={term.id}>
              {index > 0 ? ", " : ""}
              {/* **읽을 수 있어야 동의다.** 읽지 않고 누르게 하면 받은 것이 아니다. */}
              <Link href={`/terms/${term.id}`} className="underline">
                {term.title} ({term.version})
              </Link>
            </span>
          ))}
        </span>
        <Button className="ml-auto px-3 py-1 text-xs" onClick={agree}>
          동의하기
        </Button>
      </div>
    </Alert>
  );
}
