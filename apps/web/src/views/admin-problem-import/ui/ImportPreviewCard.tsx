import { CATEGORY_LABELS, difficultyLabel } from "@/entities/problem";
import type { ProblemImportPreview } from "@/entities/problem";
import { Alert, Card, CardTitle } from "@/shared/ui";
import { importWarnings } from "../model/warnings";

const TESTCASE_SOURCE_LABELS: Record<ProblemImportPreview["testcaseSource"], string> = {
  FILES: "묶음 안의 testcases 폴더",
  INLINE: "problem.json 의 testcases",
  NONE: "없음",
};

/**
 * 저장하기 전에 무엇이 들어올지 보여준다 (#538).
 *
 * **개수와 요약을 보인다.** 테스트케이스가 수백 개면 내용을 다 보일 수 없고, 몇 개만
 * 보이면 가운데가 깨진 묶음을 못 잡는다. 개수와 어디서 왔는지가 실제로 더 쓸모 있다.
 */
export function ImportPreviewCard({ preview }: { preview: ProblemImportPreview }) {
  const warnings = importWarnings(preview);

  return (
    <Card className="p-5">
      <CardTitle>{preview.title}</CardTitle>

      {/* 좁은 화면에서 두 칸이 붙지 않게 한 칸으로 떨어뜨린다 (#484). */}
      <dl className="mt-3 grid grid-cols-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
        <Row label="주소" value={preview.slug} />
        <Row label="분류" value={CATEGORY_LABELS[preview.category] ?? preview.category} />
        <Row label="유형" value={preview.problemKind} />
        <Row label="난이도" value={preview.difficulty ? difficultyLabel(preview.difficulty) : "정해지지 않음"} />
        <Row label="시간 제한" value={`${preview.timeLimitMs}ms`} />
        <Row label="메모리 제한" value={`${preview.memoryLimitMb}MB`} />
        <Row label="테스트케이스" value={`${preview.testcaseCount}개`} />
        <Row label="가져온 곳" value={TESTCASE_SOURCE_LABELS[preview.testcaseSource]} />
        <Row label="시작 코드" value={`${preview.templateCount}개`} />
      </dl>

      {/*
        무엇을 경고할지는 `importWarnings` 가 정한다 (테스트가 붙어 있다).
        여기서는 **한 번에 다 보이는 것**만 지킨다 — 하나씩 알려 주면 고치고 다시
        올리기를 반복하게 된다.
      */}
      {warnings.length > 0 ? (
        <ul className="mt-3 space-y-2">
          {warnings.map((warning) => (
            <li key={warning.message}>
              <Alert tone={warning.blocking ? "danger" : "warn"}>{warning.message}</Alert>
            </li>
          ))}
        </ul>
      ) : null}
    </Card>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-2">
      <dt className="shrink-0 text-ink-muted">{label}</dt>
      <dd className="min-w-0 break-words text-ink">{value}</dd>
    </div>
  );
}
