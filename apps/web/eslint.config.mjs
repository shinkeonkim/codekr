import next from "eslint-config-next";

/**
 * Feature-Sliced Design 계층 규칙 (#89).
 *
 * 위 계층은 아래 계층만 import 할 수 있다.
 *   app > views > widgets > features > entities > shared
 *
 * 문서로만 적어두면 지켜지지 않는다. 어긴 import 는 여기서 막는다.
 * 규칙은 ESLint 내장 no-restricted-imports 로만 쓴다 — 플러그인을 새로 들이지 않는다.
 */
const LAYERS = ["shared", "entities", "features", "widgets", "views", "app"];

/** 공개 API 를 가진 계층. shared 는 하위 모듈(@/shared/ui)이 곧 공개 단위라 제외한다. */
const SLICED = ["entities", "features", "widgets", "views"];

/**
 * 한 계층의 제약을 **한 객체로** 만든다.
 *
 * no-restricted-imports 를 여러 config 객체에 나눠 쓰면 뒤 객체가 앞 객체를 덮어쓴다
 * (같은 규칙 이름이라 병합되지 않는다). 그래서 계층 규칙과 공개 API 규칙을
 * 한 patterns 배열에 합친다.
 */
function layerConfig(layer) {
  const above = LAYERS.slice(LAYERS.indexOf(layer) + 1);

  const patterns = above.map((higher) => ({
    group: [`@/${higher}`, `@/${higher}/*`],
    message:
      `${layer} 는 ${higher} 를 import 할 수 없습니다. ` +
      "의존은 항상 아래 계층으로만 흐릅니다 (docs/11_웹_구조.md).",
  }));

  // 같은 계층의 다른 슬라이스는 공개 API(index)로만 쓴다.
  // 내부 파일을 직접 가져가면 슬라이스를 고칠 때마다 남의 코드가 깨진다.
  // 자기 슬라이스 내부는 상대 경로를 쓰므로 이 규칙에 걸리지 않는다.
  const belowSliced = SLICED.filter((s) => LAYERS.indexOf(s) <= LAYERS.indexOf(layer));
  if (belowSliced.length > 0) {
    patterns.push({
      group: belowSliced.map((s) => `@/${s}/*/*`),
      message: "슬라이스 내부 파일을 직접 import 하지 않습니다. 공개 API(index)를 쓰세요.",
    });
  }

  if (patterns.length === 0) return null;
  return {
    files: [`src/${layer}/**/*.{ts,tsx}`],
    rules: { "no-restricted-imports": ["error", { patterns }] },
  };
}

const config = [
  ...next,
  { ignores: [".next/**", "node_modules/**"] },
  ...LAYERS.map(layerConfig).filter(Boolean),
];

export default config;
