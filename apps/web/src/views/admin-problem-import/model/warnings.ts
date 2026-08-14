import type { ProblemImportPreview } from "@/entities/problem";

/**
 * 미리보기에서 사람에게 말해 줘야 할 것 (#538).
 *
 * 화면에서 떼어 둔 것은 **무엇을 경고하는지가 판단이기 때문**이다. 컴포넌트 안에
 * 두면 조건이 늘 때마다 눈으로만 확인하게 된다.
 */
export interface ImportWarning {
  /** 저장을 막는가. `false` 면 알리기만 한다. */
  blocking: boolean;
  message: string;
}

export function importWarnings(preview: ProblemImportPreview): ImportWarning[] {
  const warnings: ImportWarning[] = [];

  if (preview.testcaseCount === 0) {
    // 막지는 않는다. 테스트케이스를 나중에 붙이는 순서도 있기 때문이다.
    warnings.push({
      blocking: false,
      message: "테스트케이스가 없습니다. 이대로 만들면 채점할 것이 없는 문제가 됩니다.",
    });
  }

  if (preview.publishedInBundle) {
    // 말해 주지 않으면 "왜 공개가 안 됐지" 를 겪는다.
    warnings.push({
      blocking: false,
      message: "파일에 published: true 가 적혀 있지만 초안으로 들어갑니다.",
    });
  }

  // **서버가 이미 검증한 결과다.** 화면이 다시 판단하지 않는다 — 두 벌이 되면 갈라진다.
  preview.violations.forEach((violation) => {
    warnings.push({ blocking: true, message: violation });
  });

  return warnings;
}

/** 하나라도 막는 것이 있으면 저장 버튼을 잠근다. */
export function isBlocked(preview: ProblemImportPreview): boolean {
  return importWarnings(preview).some((warning) => warning.blocking);
}
