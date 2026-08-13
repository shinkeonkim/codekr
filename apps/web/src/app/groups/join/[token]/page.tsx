import { GroupInvitePage } from "@/views/group-invite";

export const metadata = { title: "그룹 초대" };

export default function Page({ params }: { params: Promise<{ token: string }> }) {
  return <GroupInvitePage params={params} />;
}
