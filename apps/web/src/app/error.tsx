"use client";

import { BrandCharacter, Button } from "@/shared/ui";
import Link from "next/link";
import { useEffect } from "react";

/**
 * 예상하지 못한 오류 (#261).
 *
 * **다시 시도 버튼은 진짜여야 한다.** 원본 그림에는 "TRY AGAIN" 이 그려져 있었는데,
 * 눌리지 않는 버튼 그림은 함정이라 그 부분을 잘라 내고 실제 버튼을 둔다.
 */
export default function GlobalError({ error, reset }: { error: Error; reset: () => void }) {
  useEffect(() => {
    // 사용자에게는 자세히 말하지 않는다. 다만 우리는 알아야 한다.
    console.error(error);
  }, [error]);

  return (
    <div className="flex flex-col items-center gap-6 py-16 text-center">
      <BrandCharacter name="serverError" size={220} />
      <div>
        <h1 className="text-2xl font-bold text-ink">문제가 생겼습니다</h1>
        <p className="mt-2 text-sm text-ink-muted">
          잠시 뒤 다시 시도해 보세요. 계속된다면 게시판에 알려 주세요.
        </p>
      </div>
      <div className="flex flex-wrap justify-center gap-2">
        <Button onClick={reset}>다시 시도</Button>
        <Button asChild variant="secondary"><Link href="/">첫 화면으로</Link></Button>
      </div>
    </div>
  );
}
