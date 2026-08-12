import { BrandCharacter } from "@/shared/ui";
import type { Verdict } from "../model/types";

/**
 * 판정에 맞는 캐릭터 (#261).
 *
 * **맞음과 틀림만 구분한다.** 시간 초과·메모리 초과·런타임 오류를 각각 다른 그림으로
 * 두면 그림이 판정 이름을 대신하려 드는데, 그 구분은 뱃지가 이미 정확히 한다.
 *
 * 시스템 오류에는 붙이지 않는다 — 그것은 사용자의 결과가 아니라 우리 잘못이다.
 * 채점 중에도 붙이지 않는다 — 아직 아무 일도 일어나지 않았다.
 *
 * alt 를 비운 이유: 바로 옆에 판정 뱃지가 같은 것을 글자로 말한다.
 */
export function VerdictMascot({ verdict, size = 56 }: { verdict: Verdict | null; size?: number }) {
  if (!verdict || verdict === "SYSTEM_ERROR") return null;
  return <BrandCharacter name={verdict === "ACCEPTED" ? "success" : "fail"} size={size} />;
}
