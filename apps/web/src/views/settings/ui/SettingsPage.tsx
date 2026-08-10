"use client";

import { VISIBILITY_DESCRIPTIONS, VISIBILITY_LABELS } from "@/entities/submission";
import type { SubmissionVisibility } from "@/entities/submission";
import type { NotificationCategory } from "@/entities/notification";
import { userApi } from "@/entities/user";
import type { UserSettings } from "@/entities/user";
import { RequireAuth } from "@/features/auth";
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
  const [settings, setSettings] = useState<UserSettings | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    userApi
      .settings()
      .then(setSettings)
      .catch(() => setError("설정을 불러오지 못했습니다."));
  }, []);

  const toggleCategory = async (category: NotificationCategory, muted: boolean) => {
    const next = muted
      ? settings!.mutedNotificationCategories.filter((it) => it !== category)
      : [...settings!.mutedNotificationCategories, category];
    try {
      setSettings(await userApi.updateSettings({ mutedNotificationCategories: next }));
      toast.success("알림 설정을 저장했습니다.");
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "알림 설정을 저장하지 못했습니다.");
    }
  };

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

      <Card className="space-y-3 p-5">
        <div>
          <h2 className="text-sm font-semibold text-ink">알림</h2>
          <p className="mt-1 text-xs text-ink-muted">
            받고 싶지 않은 종류를 끌 수 있습니다. 끈 동안에는 알림이 만들어지지 않으므로,
            다시 켜도 그동안의 알림은 오지 않습니다.
          </p>
        </div>

        <ul className="space-y-2">
          {settings.notificationCategories.map((option) => {
            const muted = settings.mutedNotificationCategories.includes(option.category);
            return (
              <li key={option.category} className="flex items-center gap-3">
                <span className="min-w-20 text-sm text-ink">{option.label}</span>
                {option.mutable ? (
                  <Button
                    variant={muted ? "secondary" : "primary"}
                    className="px-3 py-1 text-xs"
                    onClick={() => toggleCategory(option.category, muted)}
                  >
                    {muted ? "꺼짐" : "켜짐"}
                  </Button>
                ) : (
                  // 끌 수 없는 것은 스위치를 아예 두지 않는다 — 눌러도 안 되는 버튼은 고장으로 보인다.
                  <span className="text-xs text-ink-muted">항상 받음</span>
                )}
              </li>
            );
          })}
        </ul>
      </Card>
    </div>
  );
}
