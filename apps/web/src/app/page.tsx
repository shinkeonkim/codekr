import Link from "next/link";
import { Button, Card } from "@/components/ui";

const FEATURES = [
  {
    title: "폭넓은 문제 유형",
    body: "알고리즘과 자료구조는 물론 SQL, 네트워크, 운영체제, 시스템 설계까지 한 곳에서 준비합니다.",
  },
  {
    title: "실시간 채점 과정",
    body: "제출하면 테스트케이스가 하나씩 채점되는 과정이 화면에 그대로 흐릅니다. 결과를 기다리지 않아도 됩니다.",
  },
  {
    title: "격리된 실행 환경",
    body: "모든 코드는 네트워크가 차단된 샌드박스 컨테이너에서 실행되며, 시간·메모리 제한이 정확히 적용됩니다.",
  },
];

export default function HomePage() {
  return (
    <div className="space-y-16">
      <section className="pt-8 text-center">
        <h1 className="text-4xl font-bold tracking-tight text-ink sm:text-5xl">
          코드를 쓰고, <span className="text-brand">채점 과정을 지켜보세요</span>
        </h1>
        <p className="mx-auto mt-4 max-w-2xl text-base text-ink-muted">
          코드.kr 은 다양한 유형의 코딩 테스트 문제와 실시간 채점 환경을 제공하는 오픈소스 플랫폼입니다.
        </p>
        <div className="mt-8 flex items-center justify-center gap-3">
          <Link href="/problems">
            <Button>문제 풀러 가기</Button>
          </Link>
          <Link href="/signup">
            <Button variant="secondary">회원가입</Button>
          </Link>
        </div>
      </section>

      <section className="grid gap-4 sm:grid-cols-3">
        {FEATURES.map((feature) => (
          <Card key={feature.title} className="p-5">
            <h2 className="font-semibold text-ink">{feature.title}</h2>
            <p className="mt-2 text-sm leading-relaxed text-ink-muted">{feature.body}</p>
          </Card>
        ))}
      </section>
    </div>
  );
}
