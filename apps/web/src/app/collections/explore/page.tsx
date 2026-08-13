import { PublicCollectionsPage } from "@/views/public-collections";

export const metadata = {
  title: "공개 문제집",
  description: "다른 사람이 만든 문제집을 둘러봅니다.",
};

export default function Page() {
  return <PublicCollectionsPage />;
}
