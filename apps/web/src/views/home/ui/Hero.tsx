import { Button } from "@/shared/ui";
import Link from "next/link";
import { HeroFrame } from "./HeroFrame";

/**
 * 처음 온 사람에게 보이는 첫 화면의 글 (#261, #231, #282).
 *
 * 이미 쓰는 사람에게 소개는 지나가야 할 벽이다 — 그 사람의 자리에는 `WelcomeBack` 의
 * 글이 들어간다. **틀(`HeroFrame`)은 둘이 같은 것을 쓴다.**
 *
 * 그림 안의 글자(원본 히어로의 왼쪽 패널)는 쓰지 않는다. 화면 폭에 맞춰 줄이 바뀌지
 * 않고, 스크린 리더가 읽지 못하며, 문구를 고치려면 그림을 다시 그려야 한다.
 * **글자는 글자로 쓴다.**
 */
export function Hero() {
  return (
    <HeroFrame>
      <h1 className="text-4xl font-bold tracking-tight text-ink sm:text-5xl">
        문제로 성장하는
        <br />
        <span className="text-brand">개발자들의 온라인 저지</span>
      </h1>
      <p className="mx-auto mt-4 max-w-md text-base leading-relaxed text-ink-muted lg:mx-0">
        알고리즘·SQL·CS 문제를 풀고, 채점이 도는 과정을 실시간으로 보면서 코딩 실력을
        증명하세요.
      </p>

      <div className="mt-6 flex flex-wrap justify-center gap-2 lg:justify-start">
        <Link href="/problems">
          <Button>지금 시작하기</Button>
        </Link>
        <Link href="/signup">
          <Button variant="secondary">회원가입</Button>
        </Link>
      </div>
    </HeroFrame>
  );
}
