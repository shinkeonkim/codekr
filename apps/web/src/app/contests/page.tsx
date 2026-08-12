import { ContestListPage } from "@/views/contest-list";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "대회",
  description: "정해진 시간 안에 겨루고, 순위표가 실시간으로 움직입니다.",
};


export default function Page() {
  return <ContestListPage />;
}
