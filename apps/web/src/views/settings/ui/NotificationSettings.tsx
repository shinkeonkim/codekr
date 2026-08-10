"use client";

import type { NotificationCategory } from "@/entities/notification";
import type { UserSettings } from "@/entities/user";
import { Button, Card } from "@/shared/ui";

/** 카테고리별 알림 수신 설정 (#106). */
export function NotificationSettings({
  settings,
  onToggle,
}: {
  settings: UserSettings;
  onToggle: (category: NotificationCategory, muted: boolean) => void;
}) {
  return (
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
                  onClick={() => onToggle(option.category, muted)}
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
  );
}
