/**
 * GTM(Google Tag Manager) 스니펫.
 *
 * `NEXT_PUBLIC_GTM_ID`는 빌드 시점에 박힌다 (apps/web/Dockerfile) — 비어 있으면
 * (로컬 개발, GTM 미설정 배포) 아무것도 렌더링하지 않는다.
 */
const GTM_ID = process.env.NEXT_PUBLIC_GTM_ID;

const HEAD_SCRIPT = `
(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':
new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0],
j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src=
'https://www.googletagmanager.com/gtm.js?id='+i+dl;f.parentNode.insertBefore(j,f);
})(window,document,'script','dataLayer','${GTM_ID}');
`;

export function GoogleTagManagerScript() {
  if (!GTM_ID) return null;
  return <script dangerouslySetInnerHTML={{ __html: HEAD_SCRIPT }} />;
}

export function GoogleTagManagerNoScript() {
  if (!GTM_ID) return null;
  return (
    <noscript>
      <iframe
        src={`https://www.googletagmanager.com/ns.html?id=${GTM_ID}`}
        height="0"
        width="0"
        style={{ display: "none", visibility: "hidden" }}
        title="Google Tag Manager"
      />
    </noscript>
  );
}
