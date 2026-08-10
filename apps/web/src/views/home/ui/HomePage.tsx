import { StartHere } from "./StartHere";

/**
 * 첫 화면 (#72).
 *
 * 방문자가 알고 싶은 것은 "이 사이트가 나에게 무엇을 해주는가" 이지 "이 사이트가 어떻게
 * 동작하는가"가 아니다. 그래서 기능을 나열하는 대신 **지금 풀 문제**를 바로 보여준다.
 * 샌드박스나 실시간 채점 같은 이야기는 소개 문서의 몫이다.
 */
export function HomePage() {
  return (
    <div className="space-y-12">
      <section className="pt-6 text-center">
        <h1 className="text-4xl font-bold tracking-tight text-ink sm:text-5xl">
          코딩 테스트, <span className="text-brand">오늘 한 문제부터</span>
        </h1>
        <p className="mx-auto mt-4 max-w-xl text-base text-ink-muted">
          알고리즘부터 SQL·네트워크·운영체제까지, 난이도별로 골라 풀고 매일 이어가세요.
        </p>
      </section>

      <StartHere />
    </div>
  );
}
