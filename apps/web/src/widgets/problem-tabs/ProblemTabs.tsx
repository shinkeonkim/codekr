"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

/**
 * 문제 단위 하위 내비게이션.
 *
 * 세 화면을 **각각의 경로**로 나눈다. 탭 상태를 컴포넌트 안에 두면 새로고침이나 링크 공유에서
 * 사라지기 때문이다. 경로가 곧 상태이므로 뒤로 가기도 자연스럽게 동작한다.
 */
export function ProblemTabs({ slug }: { slug: string }) {
  const pathname = usePathname();
  /*
    주소의 한 칸을 그대로 이어 붙인다. **그 값은 번호일 수도 slug 일 수도 있다** (#204) —
    지금 링크는 전부 번호로 가지만, 옛 링크(slug)로 들어온 사람의 탭이 번호로 바뀌면
    주소가 도중에 갈린다. 들어온 대로 유지한다.
  */
  const base = `/problems/${slug}`;

  const tabs = [
    { href: base, label: "문제 내용" },
    { href: `${base}/solve`, label: "코드 제출" },
    { href: `${base}/submissions`, label: "제출 내역" },
    // 막히는 순간은 문제를 보고 있을 때다. 그 자리에서 물을 수 있어야 한다 (#139).
    { href: `${base}/questions`, label: "질문" },
  ];

  return (
    <nav aria-label="문제 화면" className="border-b border-border">
      <ul className="-mb-px flex gap-1 overflow-x-auto">
        {tabs.map((tab) => {
          const current = pathname === tab.href;
          return (
            <li key={tab.href}>
              <Link
                href={tab.href}
                aria-current={current ? "page" : undefined}
                className={`inline-block whitespace-nowrap border-b-2 px-4 py-2.5 text-sm transition ${
                  current
                    ? "border-brand font-medium text-ink"
                    : "border-transparent text-ink-muted hover:border-border hover:text-ink"
                }`}
              >
                {tab.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
