import { UserProfilePage } from "@/views/user-profile";

export default function Page(props: { params: Promise<{ nickname: string }> }) {
  return <UserProfilePage {...props} />;
}
