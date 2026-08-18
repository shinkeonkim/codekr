import { AnonymousFeedbackForm } from "@/features/anonymous-feedback";
import { Card, CardTitle, PAGE_WIDTH } from "@/shared/ui";
import Link from "next/link";

/**
 * 로그인하지 못하는 사람이 도움을 요청하는 화면 (#611).
 *
 * **로그인 화면에서 닿는다.** 가입·인증·재설정이 안 되는 사람이 마지막으로 보는 화면이
 * 거기이기 때문이다. 푸터의 저장소 링크(#603)는 "사실상 안 받는 것" 에 가까웠다.
 */
export function HelpPage() {
  return (
    <div className={`${PAGE_WIDTH.reading} space-y-4`}>
      <div>
        <h1 className="text-2xl font-bold text-ink">로그인이 안 되시나요</h1>
        <p className="mt-1 text-sm text-ink-muted">
          가입·인증 메일·비밀번호 재설정처럼 <strong className="text-ink">로그인 전에 막히는 일</strong>을
          여기로 알려 주세요.
        </p>
      </div>

      <Card className="space-y-3 p-5">
        <CardTitle>먼저 확인해 보실 것</CardTitle>
        <ul className="list-disc space-y-1 pl-5 text-sm text-ink-muted">
          <li>인증 메일이 스팸함에 있는지 — 실제로 그런 일이 있었습니다</li>
          <li>
            비밀번호를 잊으셨다면{" "}
            <Link href="/forgot-password" className="text-brand hover:underline">
              재설정
            </Link>
            을 먼저 시도해 보세요
          </li>
        </ul>
      </Card>

      <Card className="space-y-3 p-5">
        <CardTitle>그래도 안 되면 알려 주세요</CardTitle>
        <AnonymousFeedbackForm />
      </Card>

      {/* 로그인할 수 있는 사람에게는 설정의 통로가 낫다 — 처리 결과를 볼 수 있다 (#603). */}
      <p className="text-xs text-ink-muted">
        로그인할 수 있다면{" "}
        <Link href="/feedback" className="text-brand hover:underline">
          신고·제안
        </Link>{" "}
        에서 보내 주세요. 그쪽은 처리 결과를 돌려받을 수 있습니다.
      </p>
    </div>
  );
}
