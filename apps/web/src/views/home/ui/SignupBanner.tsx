import { BrandCharacter, Button, Card } from "@/shared/ui";
import Link from "next/link";

/**
 * 가입 권유 배너 (#275).
 *
 * 전에는 문제 목록 밑의 **작은 글씨 한 줄과 보조 버튼**이었다. 비로그인 방문자에게
 * 첫 화면에서 가장 하고 싶은 말인데 가장 작게 적혀 있었다.
 *
 * **무엇이 좋아지는지를 적는다.** "회원가입하세요" 는 우리가 원하는 것이고, 방문자가
 * 알고 싶은 것은 가입하면 무엇이 달라지는가다 — 기록이 쌓이고, 이어 온 날이 세어지고,
 * 남들과 견줄 수 있게 된다.
 *
 * 로그인한 사람에게는 보이지 않는다 (#73). 그 판단은 `HomePage` 가 한다.
 */
export function SignupBanner() {
  return (
    <Card className="flex flex-col items-center gap-6 overflow-hidden bg-surface-muted/40 p-8 text-center sm:flex-row sm:p-10 sm:text-left">
      <BrandCharacter name="welcome" size={160} className="shrink-0" />

      <div className="min-w-0 flex-1">
        <h2 className="text-2xl font-bold tracking-tight text-ink sm:text-3xl">
          기록으로 남는 풀이를 시작하세요
        </h2>
        <p className="mt-2 text-sm leading-relaxed text-ink-muted">
          가입하면 푼 문제와 연속 학습 기록이 쌓이고, 실력 점수로 순위에 들어갑니다.
          이메일 하나면 됩니다.
        </p>
      </div>

      <div className="flex shrink-0 flex-col gap-2 sm:w-40">
        <Link href="/signup">
          <Button className="w-full">회원가입</Button>
        </Link>
        {/* 이미 계정이 있는 사람이 배너 앞에서 막히지 않게 한다. */}
        <Link href="/login">
          <Button variant="secondary" className="w-full">
            로그인
          </Button>
        </Link>
      </div>
    </Card>
  );
}
