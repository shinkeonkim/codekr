import { GroupDetailPage } from "@/views/group-detail";

export const metadata = { title: "그룹" };

export default function Page({ params }: { params: Promise<{ id: string }> }) {
  return <GroupDetailPage params={params} />;
}
