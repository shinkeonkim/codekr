"use client";

import type { QuizAnswerType, QuizSpec } from "@/entities/problem";
import { QUIZ_ANSWER_TYPE_LABELS } from "@/entities/problem";
import { Button, CheckboxField, Field, Input, Select, Textarea } from "@/shared/ui";

/**
 * 퀴즈의 보기·정답·해설 (#650).
 *
 * **유형에 따라 칸이 갈린다.** 객관식이면 보기를, 단답이면 받아 줄 답을 받는다 —
 * 둘을 함께 실어 보내면 서버가 거부한다. 한 칸에 몰지 않은 이유는 그 둘이 다른
 * 것이기 때문이다: 보기는 사람에게 보이고, 받아 줄 답은 보이지 않는다.
 */
export function QuizSpecEditor({
  value,
  onChange,
}: {
  value: QuizSpec;
  onChange: (next: QuizSpec) => void;
}) {
  const update = <K extends keyof QuizSpec>(key: K, next: QuizSpec[K]) =>
    onChange({ ...value, [key]: next });

  const usesChoices = value.answerType !== "SHORT";

  /** 유형을 바꾸면 **그 유형이 쓰지 않는 칸을 비운다** — 서버가 섞인 것을 거부한다. */
  const changeType = (next: QuizAnswerType) =>
    onChange({
      ...value,
      answerType: next,
      choices: next === "SHORT" ? [] : value.choices,
      answers: next === "SHORT" ? value.answers : [],
    });

  return (
    <div className="space-y-4">
      <p className="text-xs text-ink-muted">
        퀴즈는 <b>실행기를 쓰지 않습니다</b> — 답을 내면 그 자리에서 채점됩니다. 그래서
        시간·메모리 제한도 언어도 없습니다. 그리고 <b>난이도를 매기지 않습니다</b>:
        찍어서 맞는 문제가 랭킹 점수에 들어가면 순위의 뜻이 옅어집니다.
      </p>

      <Field label="답 받는 방식">
        <Select
          value={value.answerType}
          onChange={(event) => changeType(event.target.value as QuizAnswerType)}
        >
          {(Object.keys(QUIZ_ANSWER_TYPE_LABELS) as QuizAnswerType[]).map((type) => (
            <option key={type} value={type}>
              {QUIZ_ANSWER_TYPE_LABELS[type]}
            </option>
          ))}
        </Select>
      </Field>

      {usesChoices ? (
        <ChoiceList value={value} onChange={onChange} />
      ) : (
        <AnswerList value={value} onChange={onChange} />
      )}

      <Field label="해설">
        <p className="text-xs text-ink-muted">
          채점이 끝난 뒤에만 보입니다. <b>틀린 사람에게 더 필요합니다</b> — 4지선다는
          판정만으로 배울 것이 없습니다.
        </p>
        <Textarea
          rows={4}
          value={value.explanation ?? ""}
          onChange={(event) => update("explanation", event.target.value || null)}
          placeholder="TCP 는 전송 계층(4계층) 프로토콜입니다. …"
        />
      </Field>
    </div>
  );
}

/** 객관식 보기. 정답 표시는 **사용자에게 나가지 않는다.** */
function ChoiceList({ value, onChange }: { value: QuizSpec; onChange: (next: QuizSpec) => void }) {
  const setChoices = (choices: QuizSpec["choices"]) => onChange({ ...value, choices });

  return (
    <Field label="보기">
      <div className="space-y-2">
        <p className="text-xs text-ink-muted">
          정답을 하나 이상 골라 주세요. 순서는 적은 대로 보입니다.
        </p>
        {value.choices.map((choice, index) => (
          <div key={index} className="flex items-center gap-2">
            <Input
              value={choice.content}
              onChange={(event) =>
                setChoices(
                  value.choices.map((it, i) =>
                    i === index ? { ...it, content: event.target.value } : it,
                  ),
                )
              }
              placeholder={`보기 ${index + 1}`}
            />
            <CheckboxField
              label="정답"
              checked={choice.correct}
              onCheckedChange={(checked) =>
                setChoices(
                  value.choices.map((it, i) =>
                    i === index
                      ? { ...it, correct: checked }
                      : // 하나만 고르는 문제는 정답도 하나다 — 서버가 그것을 요구한다.
                        value.answerType === "SINGLE" && checked
                        ? { ...it, correct: false }
                        : it,
                  ),
                )
              }
            />
            <Button
              type="button"
              variant="ghost"
              onClick={() => setChoices(value.choices.filter((_, i) => i !== index))}
            >
              삭제
            </Button>
          </div>
        ))}
        <Button
          type="button"
          variant="secondary"
          onClick={() => setChoices([...value.choices, { content: "", correct: false }])}
        >
          보기 추가
        </Button>
      </div>
    </Field>
  );
}

/** 단답으로 받아 줄 답. **동의어를 여기서 푼다** — 정규화로는 이을 수 없다. */
function AnswerList({ value, onChange }: { value: QuizSpec; onChange: (next: QuizSpec) => void }) {
  const setAnswers = (answers: string[]) => onChange({ ...value, answers });

  return (
    <div className="space-y-3">
      <Field label="받아 줄 답">
        <div className="space-y-2">
          <p className="text-xs text-ink-muted">
            같은 뜻의 다른 표기를 모두 적어 주세요 (TCP · 전송 제어 프로토콜).
            대소문자·공백 규칙으로는 이을 수 없습니다.
          </p>
          {value.answers.map((answer, index) => (
            <div key={index} className="flex items-center gap-2">
              <Input
                value={answer}
                onChange={(event) =>
                  setAnswers(value.answers.map((it, i) => (i === index ? event.target.value : it)))
                }
                placeholder={`답 ${index + 1}`}
              />
              <Button
                type="button"
                variant="ghost"
                onClick={() => setAnswers(value.answers.filter((_, i) => i !== index))}
              >
                삭제
              </Button>
            </div>
          ))}
          <Button type="button" variant="secondary" onClick={() => setAnswers([...value.answers, ""])}>
            답 추가
          </Button>
        </div>
      </Field>

      <div className="flex gap-4">
        <CheckboxField
          label="대소문자 무시"
          checked={value.ignoreCase}
          onCheckedChange={(checked) => onChange({ ...value, ignoreCase: checked })}
        />
        <CheckboxField
          label="공백 무시"
          checked={value.ignoreWhitespace}
          onCheckedChange={(checked) => onChange({ ...value, ignoreWhitespace: checked })}
        />
      </div>
      <p className="text-xs text-ink-muted">
        <b>문제마다 다릅니다.</b> <code>TCP</code>/<code>tcp</code> 는 같게 보고 싶지만,
        <code>chmod</code> 의 <code>X</code> 와 <code>x</code> 는 다른 것을 가리킵니다.
      </p>
    </div>
  );
}
