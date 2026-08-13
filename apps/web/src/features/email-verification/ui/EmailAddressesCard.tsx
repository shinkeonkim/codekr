"use client";

import { userApi } from "@/entities/user";
import type { UserEmail } from "@/entities/user";
import { useAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { Button, Card, CardTitle, ConfirmDialog, Input, useToast } from "@/shared/ui";
import { useEffect, useState } from "react";

/**
 * 메일 주소 여러 개 (#430, #396 화면).
 *
 * **로그인 주소를 학교 메일로 바꾸게 하지 않는다.** 그러면 졸업하는 순간 로그인을
 * 잃는다 — 그래서 주소를 *더한다*. 소속(#398)은 여기 더한 주소에 붙는다.
 */
export function EmailAddressesCard() {
  const { user } = useAuth();
  const toast = useToast();
  const [emails, setEmails] = useState<UserEmail[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);

  const reload = () =>
    userApi
      .emails()
      .then(setEmails)
      .catch(() => setEmails([]));

  useEffect(() => {
    reload();
  }, []);

  const add = async () => {
    if (!input.trim()) {
      toast.error("메일 주소를 입력해 주세요.");
      return;
    }
    setSending(true);
    try {
      await userApi.addEmail(input.trim().toLowerCase());
      // **아직 목록에 없다.** 링크를 눌러야 확인이 끝난다 — 그 사실을 그대로 말한다.
      toast.success("확인 메일을 보냈습니다. 링크를 누르면 목록에 나타납니다.");
      setInput("");
    } catch (caught) {
      // 이미 누가 쓰는 주소·쿨다운·하루 상한은 서버가 이유를 담아 준다 (#396, #233).
      toast.error(caught instanceof ApiError ? caught.message : "보내지 못했습니다.");
    } finally {
      setSending(false);
    }
  };

  const remove = async (id: number) => {
    try {
      await userApi.removeEmail(id);
      toast.success("뗐습니다.");
      await reload();
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "떼지 못했습니다.");
    }
  };

  return (
    <Card className="space-y-3 p-5">
      <div>
        <CardTitle>메일 주소</CardTitle>
        <p className="mt-1 text-xs text-ink-muted">
          학교·회사 메일을 더해 두면 소속을 붙일 수 있습니다. 로그인은 그대로 아래 주소로
          합니다.
        </p>
      </div>

      {/* 로그인 주소는 뗄 수 없다 — 떼면 들어올 길이 없다. 그래서 목록 밖에 둔다. */}
      <p className="text-sm text-ink">
        {user?.email} <span className="text-xs text-ink-muted">(로그인 주소)</span>
      </p>

      {emails.map((each) => (
        <div key={each.id} className="flex items-center gap-2 text-sm text-ink">
          <span className="flex-1 truncate">{each.email}</span>
          <ConfirmDialog
            trigger={
              <Button variant="ghost" className="px-2 py-0.5 text-xs">
                떼기
              </Button>
            }
            title={`${each.email} 을 뗍니다`}
            description="이 주소로 붙은 소속도 함께 떨어집니다. 다시 더하려면 확인 메일을 한 번 더 받아야 합니다."
            confirmLabel="떼기"
            onConfirm={() => remove(each.id)}
          />
        </div>
      ))}

      <div className="flex gap-2">
        <Input
          type="email"
          placeholder="학교·회사 메일 주소"
          value={input}
          onChange={(event) => setInput(event.target.value)}
        />
        <Button variant="secondary" disabled={sending} onClick={add}>
          확인 메일 받기
        </Button>
      </div>
    </Card>
  );
}
