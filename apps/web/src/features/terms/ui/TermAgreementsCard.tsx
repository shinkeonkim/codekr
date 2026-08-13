"use client";

import { userApi } from "@/entities/user";
import type { TermAgreement } from "@/entities/user";
import { Card, CardTitle } from "@/shared/ui";
import { formatDateTime } from "@/shared/lib";
import Link from "next/link";
import { useEffect, useState } from "react";

/** 내가 동의한 내역 (#235). 무엇에, 어느 판에, 언제 동의했는지. */
export function TermAgreementsCard() {
  const [agreements, setAgreements] = useState<TermAgreement[]>([]);

  useEffect(() => {
    userApi
      .termAgreements()
      .then(setAgreements)
      .catch(() => setAgreements([]));
  }, []);

  if (agreements.length === 0) return null;

  return (
    <Card className="space-y-3 p-5">
      <div>
        <CardTitle>동의한 약관</CardTitle>
        <p className="mt-1 text-xs text-ink-muted">언제 어느 판에 동의했는지 남습니다.</p>
      </div>
      <ul className="space-y-1 text-xs text-ink-muted">
        {agreements.map((agreement) => (
          <li key={agreement.documentId}>
            <Link href={`/terms/${agreement.documentId}`} className="text-brand hover:underline">
              {agreement.title} ({agreement.version})
            </Link>{" "}
            · {formatDateTime(agreement.agreedAt)}
          </li>
        ))}
      </ul>
    </Card>
  );
}
