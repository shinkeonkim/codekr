"use client";

import { VISIBILITY_DESCRIPTIONS, VISIBILITY_LABELS } from "@/entities/submission";
import type { SubmissionVisibility } from "@/entities/submission";
import { userApi } from "@/entities/user";
import type { UserSettings } from "@/entities/user";
import { AvatarEditor } from "@/features/avatar-editor";
import { useAuth } from "@/features/auth";
import { RequireAuth } from "@/features/auth";
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
      .then(setSettings)
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

      <Card className="space-y-3 p-5">
        <div>
          <h2 className="text-sm font-semibold text-ink">프로필 이미지</h2>
          <p className="mt-1 text-xs text-ink-muted">
            목록과 순위표에서 사람을 구분하는 데 쓰입니다. 올리지 않으면 닉네임 첫 글자가 보입니다.
          </p>
        </div>
        {user ? (
          <AvatarEditor
            nickname={user.nickname}
            avatarUrl={user.avatarUrl}
            // 헤더와 프로필이 같은 값을 쓰므로 바뀌면 사용자 정보를 다시 받는다.
            onChange={() => refresh()}
          />
        ) : null}
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

      <WithdrawalCard />

    </div>
  );
}
