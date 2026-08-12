import { BrandCharacter, Button } from "@/shared/ui";
import Link from "next/link";

/**
 * 없는 주소 (#261).
 *
 * **길을 못 찾은 것은 온 사람 잘못이 아니다.** 그래서 오답 얼굴(`fail`)을 쓰지 않는다 —
 * 그것은 "네가 틀렸다" 로 읽힌다. 갸웃하는 장면을 쓰고, 나가는 길을 함께 준다.
 *
 * 그림 안에 있던 안내판("PAGE NOT FOUND")은 잘라 냈다. 안내는 화면이 글자로 한다.
 */
export default function NotFound() {
  return (
    <div className="flex flex-col items-center gap-6 py-16 text-center">
      <BrandCharacter name="notFound" size={340} />
      <div>
        <h1 className="text-2xl font-bold text-ink">찾는 페이지가 없습니다</h1>
        <p className="mt-2 text-sm text-ink-muted">
          주소가 바뀌었거나, 지워진 글일 수 있습니다.
        </p>
      </div>
      <div className="flex flex-wrap justify-center gap-2">
        <Link href="/">
          <Button>첫 화면으로</Button>
        </Link>
        <Link href="/problems">
          <Button variant="secondary">문제 보러 가기</Button>
        </Link>
      </div>
    </div>
  );
}
