/**
 * 로그인 없이 열리는 화면들 (#234).
 *
 * **Footer 와 sitemap.xml 이 같은 목록을 본다.** 두 곳에 손으로 적어 두면 화면이
 * 늘어날 때마다 한쪽만 고쳐지고, 어느 쪽이 사실인지 알 수 없게 된다.
 *
 * 로그인해야 열리는 화면(내 제출·설정·알림)은 여기 없다. 눌렀을 때 로그인으로 튕기는
 * 링크는 **고장으로 보이고**, 색인되어도 검색 결과에서 로그인 화면이 나온다.
 */
export const PUBLIC_ROUTES = {
  풀기: [
    { href: "/problems", label: "문제" },
    { href: "/collections", label: "문제집" },
    { href: "/contests", label: "대회" },
  ],
  둘러보기: [
    { href: "/submissions/explore", label: "전체 제출" },
    { href: "/ranking", label: "랭킹" },
    { href: "/posts", label: "게시판" },
  ],
} as const;

/**
 * 색인하면 안 되는 경로.
 *
 * 어드민과 개인 화면이다. 검색에 걸릴 이유가 없고, 걸리면 **남의 설정 화면 주소가
 * 검색 결과에 뜨는** 모양이 된다.
 *
 * 막는 것은 색인이지 접근이 아니다 — 접근은 서버가 막는다 (`SecurityConfig`).
 * robots.txt 는 요청하는 쪽이 지킬 때만 지켜지는 약속이므로, 여기 적는 것만으로
 * 보호가 되었다고 여기면 안 된다.
 */
// `/badge` 는 사람이 읽는 문서가 아니라 그림이다 (#475). 검색에 뜰 이유가 없고,
// 서버도 같은 뜻으로 `X-Robots-Tag: noindex` 를 함께 붙인다 — 크롤러가 robots.txt 를
// 보지 않고 주소를 직접 물어도 같은 답이 되게.
export const NO_INDEX_PATHS = [
  "/admin",
  "/settings",
  "/notifications",
  "/login",
  "/signup",
  "/badge",
];
