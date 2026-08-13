import { AdminContestFormPage } from "@/views/admin-contests";
import { use } from "react";

export const metadata = { title: "대회 수정" };

export default function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return <AdminContestFormPage id={Number(id)} />;
}
