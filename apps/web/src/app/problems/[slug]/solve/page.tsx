import { ProblemSolvePage } from "@/views/problem-solve";
import type { Metadata } from "next";

/**
 * 네 탭이 **하나의 정본 주소**를 가리킨다 (#278).
 *
 * 탭은 같은 문제를 다른 각도로 보는 것이지 다른 문서가 아니다. 정본을 안 정하면
 * 검색엔진이 넷을 중복으로 보고, 어느 것을 결과에 낼지 스스로 고른다.
 *
 * **여기서는 API 를 부르지 않는다.** 문제 제목을 제목에 넣으려면 서버가 API 를 부를
 * 주소를 알아야 하는데 그것이 아직 없다 (#271). 주소만으로 만들 수 있는 것까지 한다.
 */
export async function generateMetadata(props: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await props.params;
  return {
    title: "코드 제출",
    robots: { index: false, follow: false },
    alternates: { canonical: `/problems/${slug}` },
  };
}

export default function Page(props: { params: Promise<{ slug: string }> }) {
  return <ProblemSolvePage {...props} />;
}
