"use client";

import { ApiError } from "@/shared/api";
import { problemDraftApi } from "../api";
import type { ProblemDraft } from "../api";
import { Alert, Button, Field, Textarea, useToast } from "@/shared/ui";
import { useState } from "react";
import type { ProblemFormValues } from "../model/values";
import { EMPTY_TESTCASE } from "../model/values";


/**
 * 지문에서 초안 만들기 (#230).
 *
 * **자동 등록이 아니다.** 채워 넣기만 하고 저장은 사람이 누른다 — 잘못 뽑힌 예제
 * 하나가 모든 제출을 틀리게 만들기 때문이다.
 *
 * **도구가 죽어도 폼은 산다.** 실패하면 알리고 끝이다. 채워진 값을 지우지 않고,
 * 손으로 쓰던 것을 건드리지 않는다. 이 구획을 아예 열지 않아도 문제는 등록된다.
 */
export function DraftFromStatement({
  onFill,
}: {
  onFill: (patch: Partial<ProblemFormValues>, filled: (keyof ProblemFormValues)[]) => void;
}) {
  const toast = useToast();
  const [statement, setStatement] = useState("");
  const [working, setWorking] = useState(false);
  const [missing, setMissing] = useState<string[] | null>(null);

  const run = async () => {
    setWorking(true);
    setMissing(null);
    try {
      const draft: ProblemDraft = await problemDraftApi.fromStatement(statement);
      const patch: Partial<ProblemFormValues> = {
        // **지문 자체는 붙여 넣은 것을 그대로 쓴다.** 모델이 다시 쓴 지문을 넣으면
        // 원문과 달라지고, 그 차이를 사람이 알아채기 어렵다.
        description: statement,
        title: draft.title,
        inputDescription: draft.inputDescription,
        outputDescription: draft.outputDescription,
      };
      const filled: (keyof ProblemFormValues)[] = [
        "title",
        "inputDescription",
        "outputDescription",
        "description",
      ];

      if (draft.category) {
        patch.category = draft.category;
        filled.push("category");
      }
      if (draft.timeLimitMs) {
        patch.timeLimitMs = draft.timeLimitMs;
        filled.push("timeLimitMs");
      }
      if (draft.memoryLimitMb) {
        patch.memoryLimitMb = draft.memoryLimitMb;
        filled.push("memoryLimitMb");
      }
      if (draft.examples.length > 0) {
        /*
          예제는 **공개 테스트케이스**로 들어간다 (#39 와 연결).

          그대로 채점에 쓰이는 값이라, 저장 전에 정답 코드로 검증하는 단계가 이미
          있다. 여기서 하는 일은 옮겨 적기를 대신하는 것까지다.
        */
        patch.testcases = draft.examples.map((each, index) => ({
          ...EMPTY_TESTCASE,
          seq: index + 1,
          input: each.input,
          expectedOutput: each.output,
          // 지문의 예제는 **공개**다 — 사람이 문제 화면에서 보는 그것이다.
          visibility: "PUBLIC" as const,
        }));
        filled.push("testcases");
      }

      onFill(patch, filled);
      setMissing(draft.missing);
      toast.success("초안을 채웠습니다. 저장하기 전에 확인해 주세요.");
    } catch (caught) {
      /*
        **실패해도 폼은 그대로다.** 여기서 하는 일은 알리는 것뿐이고, 손으로 채우는
        길은 처음부터 막히지 않았다.
      */
      toast.error(caught instanceof ApiError ? caught.message : "초안을 만들지 못했습니다.");
    } finally {
      setWorking(false);
    }
  };

  return (
    <div className="space-y-3">
      <Alert tone="info">
        여기서 만든 것은 <strong>초안</strong>입니다. 채워진 칸에 표시가 붙고,{" "}
        <strong>저장은 확인한 뒤 사람이 누릅니다.</strong> 예제는 공개 테스트케이스로
        들어가며 정답 코드 검증을 거쳐야 공개할 수 있습니다.
      </Alert>
      <Field label="지문 붙여 넣기">
        <Textarea
          rows={6}
          value={statement}
          onChange={(event) => setStatement(event.target.value)}
          placeholder="문제 지문을 그대로 붙여 넣으세요. 제목·입출력 설명·예제를 뽑아냅니다."
        />
      </Field>
      <Button type="button" variant="secondary" disabled={working || !statement.trim()} onClick={run}>
        {working ? "만드는 중…" : "초안 만들기"}
      </Button>

      {/* **못 찾은 것을 감추지 않는다.** 지어낸 값보다 빈 칸이 낫고, 빈 칸보다 "왜 비었는지"가 낫다. */}
      {missing && missing.length > 0 ? (
        <Alert tone="warn">지문에서 찾지 못한 것: {missing.join(", ")} — 손으로 채워 주세요.</Alert>
      ) : null}
    </div>
  );
}
