"use client";

import { userApi } from "@/entities/user";
import { ApiError } from "@/shared/api";
import { Button, Field, Input, Select } from "@/shared/ui";
import { useState } from "react";

/** 미리 정해 둔 기간 (#224). 임의로 적게 하면 사람마다 기준이 달라진다. */
const DURATIONS = [
  { value: "3", label: "3일" },
  { value: "7", label: "7일" },
  { value: "30", label: "30일" },
  // **기한 없음은 강제 탈퇴와 다르다** — 되돌릴 수 있고 기록이 남는다.
  { value: "", label: "기한 없음" },
];

/**
 * 회원 정지 (#224).
 *
 * 지금까지 회원에게 할 수 있는 일은 **"그대로 두기" 와 "되돌릴 수 없이 지우기"(#140)**
 * 둘뿐이었다. 댓글 스팸 하나에 계정을 영구히 지우는 것은 과하다.
 */
export function SuspendForm({
  userId,
  onDone,
  onError,
}: {
  userId: number;
  onDone: (message: string) => void;
  onError: (message: string) => void;
}) {
  const [scope, setScope] = useState("WRITE");
  const [days, setDays] = useState("7");
  const [reason, setReason] = useState("");
  const [running, setRunning] = useState(false);

  const submit = async () => {
    setRunning(true);
    try {
      await userApi.suspend(userId, { scope, reason: reason.trim(), days: days ? Number(days) : null });
      setReason("");
      onDone("정지했습니다. 본인에게 알림이 갑니다.");
    } catch (caught) {
      onError(caught instanceof ApiError ? caught.message : "정지에 실패했습니다.");
    } finally {
      setRunning(false);
    }
  };

  return (
    <div className="space-y-2">
      <Field label="막을 범위">
        {/* 댓글 스팸 때문에 문제 풀이까지 막을 이유는 없다. 읽기는 어떤 값으로도 막지 않는다. */}
        <Select value={scope} onChange={(event) => setScope(event.target.value)}>
          <option value="WRITE">쓰기 (글·댓글·문제집)</option>
          <option value="SUBMIT">제출 (채점·예제 실행)</option>
          <option value="ALL">쓰기·제출 모두</option>
        </Select>
      </Field>
      <Field label="기간">
        <Select value={days} onChange={(event) => setDays(event.target.value)}>
          {DURATIONS.map((each) => (
            <option key={each.label} value={each.value}>
              {each.label}
            </option>
          ))}
        </Select>
      </Field>
      <Field label="사유">
        {/* 막힌 사람이 이 문장을 그대로 읽는다. */}
        <Input
          placeholder="본인에게 그대로 보입니다"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
        />
      </Field>
      <Button variant="danger" disabled={running || reason.trim().length === 0} onClick={submit}>
        정지
      </Button>
    </div>
  );
}
