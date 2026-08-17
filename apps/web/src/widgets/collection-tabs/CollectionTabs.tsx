"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

/**
 * 문제집 하위 내비게이션 (#601).
 *
 * **공개 문제집으로 가는 길이 없었다.** 화면(#208)은 만들어 놓고 내비에는 `/collections`
 * 하나만 있어서, 남이 만든 문제집을 보려면 주소를 알아야 했다.
 *
 * **탭 상태를 경로로 둔다** — `ProblemTabs`(#35)와 같은 이유다. 컴포넌트 안에 두면
 * 새로고침이나 링크 공유에서 사라지고, 뒤로 가기도 어긋난다. 덤으로 **이미 공유된
 * `/collections/explore` 링크가 그대로 살아 있다** — 옮길 것이 없다.
 */
export function CollectionTabs() {
  const pathname = usePathname();

  const tabs = [
    { href: "/collections", label: "내 문제집" },
    { href: "/collections/explore", label: "공개 문제집" },
  ];

  return (
    <nav aria-label="문제집" className="border-b border-border">
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
