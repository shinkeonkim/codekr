"use client";

import { Card, Field, Textarea } from "@/shared/ui";
import type { ProblemFormValues } from "../model/values";

/** 지문 (#127). 문제 설명과 입출력 설명. */
export function ProblemDescriptionFields({
  values,
  onChange,
}: {
  values: ProblemFormValues;
  onChange: <K extends keyof ProblemFormValues>(key: K, value: ProblemFormValues[K]) => void;
}) {
  const update = onChange;

  return (
      <Card className="space-y-4 p-5">
        <Field label="문제 설명">
          <Textarea
            rows={8}
            value={values.description}
            onChange={(event) => update("description", event.target.value)}
            required
          />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="입력 형식">
            <Textarea
              rows={3}
              value={values.inputDescription}
              onChange={(event) => update("inputDescription", event.target.value)}
            />
          </Field>
          <Field label="출력 형식">
            <Textarea
              rows={3}
              value={values.outputDescription}
              onChange={(event) => update("outputDescription", event.target.value)}
            />
          </Field>
        </div>
      </Card>
  );
}
