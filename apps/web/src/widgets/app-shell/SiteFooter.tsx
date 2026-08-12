import { PUBLIC_ROUTES } from "@/shared/config/routes";
import { BrandCharacter, BrandWordmark } from "@/shared/ui";
import Link from "next/link";

/**
 * 사이트 푸터 (#234, #261).
 *
 * 전에는 한 줄이었다 — 갈 곳이 없고, 이 사이트가 무엇인지도 말하지 않았다.
 * **바닥은 길을 잃은 사람이 닿는 곳이다.** 목록을 두 갈래로 나눠 둔다.
 *
 * 캐릭터를 여기 둔 이유: 페이지 끝은 **잠깐 멈추는 자리**라 그림이 방해가 되지 않는다.
 * 좁은 화면에서는 감춘다 — 거기서는 링크가 먼저다.
 */
export function SiteFooter() {
  return (
    <footer className="mt-16 border-t border-border bg-surface-muted/30">
      <div className="mx-auto grid max-w-6xl gap-8 px-4 py-10 sm:grid-cols-2 lg:grid-cols-[1.3fr_repeat(3,1fr)_auto]">
        <div>
          <BrandWordmark height={30} />
          <p className="mt-3 max-w-xs text-sm leading-relaxed text-ink-muted">
            알고리즘·SQL·CS 문제를 풀고, 채점이 도는 과정을 실시간으로 보면서 코딩 실력을
            증명하는 오픈소스 온라인 저지입니다.
          </p>
        </div>

        {/* 목록을 여기 적지 않는다 — sitemap.xml 과 같은 것을 본다 (#234). */}
        {Object.entries(PUBLIC_ROUTES).map(([title, links]) => (
          <FooterColumn key={title} title={title} links={links} />
        ))}

        <FooterColumn
          title="만든 곳"
          links={[
            { href: REPOSITORY_URL, label: "GitHub 저장소" },
            { href: `${REPOSITORY_URL}/blob/main/LICENSE`, label: "MIT 라이선스" },
            { href: `${REPOSITORY_URL}/issues`, label: "문제 신고·제안" },
          ]}
        />

        {/* 장식이므로 좁은 화면에서는 자리를 내준다. */}
        <BrandCharacter name="laptop" size={180} className="hidden justify-self-end lg:block" />
      </div>

      <div className="border-t border-border/60 py-4 text-center text-xs text-ink-muted">
        코드.kr · 오픈소스 코딩 테스트 플랫폼
      </div>
    </footer>
  );
}

const REPOSITORY_URL = "https://github.com/shinkeonkim/codekr";

function FooterColumn({
  title,
  links,
}: {
  title: string;
  links: readonly { readonly href: string; readonly label: string }[];
}) {
  return (
    <div>
      <h2 className="text-xs font-semibold uppercase tracking-wide text-ink-muted">{title}</h2>
      <ul className="mt-3 space-y-2">
        {links.map((link) => (
          <li key={link.href}>
            <Link href={link.href} className="text-sm text-ink transition hover:text-brand">
              {link.label}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
