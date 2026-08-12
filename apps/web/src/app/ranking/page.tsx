import { RankingPage } from "@/views/ranking";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "랭킹",
  description: "가장 어려운 100문제의 난이도 점수 합으로 겨룹니다.",
};


export default function Page() {
  return <RankingPage />;
}
