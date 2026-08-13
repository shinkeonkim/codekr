"use client";

import { VISIBILITY_DESCRIPTIONS, VISIBILITY_LABELS } from "@/entities/submission";
import type { SubmissionVisibility } from "@/entities/submission";
import { userApi } from "@/entities/user";
import type { UserSettings } from "@/entities/user";
import { AvatarEditor } from "@/features/avatar-editor";
import { EmailVerificationCard } from "@/features/email-verification";
import { TermAgreementsCard } from "@/features/terms";
import { BioEditor } from "@/features/profile-bio";
import { useAuth } from "@/features/auth";
import { RequireAuth } from "@/features/auth";
import { ThemePicker, applyAccountTheme, fromServer } from "@/features/theme";
import { WithdrawalCard } from "./WithdrawalCard";
import { ApiError } from "@/shared/api";
import { Alert, Button, Card, EmptyState, Select, useToast } from "@/shared/ui";
import { useEffect, useState } from "react";

export function SettingsPage() {
  return (
    <RequireAuth>
      <SettingsView />
    </RequireAuth>
  );
}

function SettingsView() {
  const toast = useToast();
  const { user, refresh } = useAuth();
  const [settings, setSettings] = useState<UserSettings | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    userApi
      .settings()
      .then((loaded) => {
        setSettings(loaded);
        // 계정에 저장된 테마가 이 기기의 선택을 이긴다 (#274). 고른 적이 없으면
        // 그대로 둔다 — 서버가 모른다고 덮어쓰면 안 된다.
        applyAccountTheme(fromServer(loaded.theme));
      })
      .catch(() => setError("설정을 불러오지 못했습니다."));
  }, []);

  const change = async (visibility: SubmissionVisibility) => {
    setError(null);
    try {
      // 바꾼 항목만 보낸다. 전체를 보내면 새 항목이 생겼을 때 옛 화면이 그것을 지운다.
      setSettings(await userApi.updateSettings({ defaultSubmissionVisibility: visibility }));
      toast.success("설정을 저장했습니다.");
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "설정을 저장하지 못했습니다.");
    }
  };

  if (error && !settings) return <EmptyState title={error} />;
  if (!settings) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  return (
    <div className="mx-auto max-w-2xl space-y-5">
      <header>
        <h1 className="text-2xl font-bold text-ink">설정</h1>
      </header>

      {error ? <Alert>{error}</Alert> : null}

      {/* 확인이 끝났으면 아무것도 그리지 않는다 (#233). 맨 위인 이유는 할 일이기 때문이다. */}
      <EmailVerificationCard />

      <Card className="space-y-3 p-5">
        <div>
          <h2 className="text-sm font-semibold text-ink">남에게 보이는 프로필</h2>
          <p className="mt-1 text-xs text-ink-muted">
            이미지는 목록과 순위표에서 사람을 구분하는 데 쓰입니다 — 올리지 않으면 닉네임 첫
            글자가 보입니다. 소개는 프로필을 여는 사람에게 보입니다.
          </p>
        </div>
        {user ? (
          <>
          <AvatarEditor
            nickname={user.nickname}
            avatarUrl={user.avatarUrl}
            // 헤더와 프로필이 같은 값을 쓰므로 바뀌면 사용자 정보를 다시 받는다.
            onChange={() => refresh()}
          />
          {/*
            소개도 아바타와 같은 성격이다 (#310) — 본인이 쓰고, 남에게 보인다.
            같은 카드에 두어 "남에게 보이는 것" 이 한자리에 모이게 한다.
          */}
          <BioEditor bio={user.bio} onChange={() => refresh()} />
          </>
        ) : null}
      </Card>

      <Card className="space-y-3 p-5">
        <div>
          <h2 className="text-sm font-semibold text-ink">화면 테마</h2>
          <p className="mt-1 text-xs text-ink-muted">
            로그인해 두면 다른 기기에서도 같은 테마로 열립니다.
          </p>
        </div>
        <ThemePicker />
      </Card>

      <Card className="space-y-3 p-5">
        <div>
          <h2 className="text-sm font-semibold text-ink">제출 소스 코드 기본 공개 범위</h2>
          <p className="mt-1 text-xs text-ink-muted">
            새로 제출할 때 이 값이 기본으로 적용됩니다. 제출할 때 따로 고를 수도 있습니다.
          </p>
        </div>

        <Select
          aria-label="기본 공개 범위"
          value={settings.defaultSubmissionVisibility}
          onChange={(event) => change(event.target.value as SubmissionVisibility)}
        >
          {Object.entries(VISIBILITY_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>

        <p className="text-xs text-ink-muted">
          {VISIBILITY_DESCRIPTIONS[settings.defaultSubmissionVisibility]}
        </p>

        {/* 이미 낸 제출이 바뀐다고 오해하지 않게 못박는다. */}
        <p className="text-xs text-ink-muted">
          이미 제출한 코드의 공개 범위는 바뀌지 않습니다. 제출 상세에서 하나씩 바꿀 수 있습니다.
        </p>
      </Card>

      {/* 내가 무엇에 동의했는지 확인할 수 있어야 한다 (#235). */}
      <TermAgreementsCard />

      <WithdrawalCard />

    </div>
  );
}
