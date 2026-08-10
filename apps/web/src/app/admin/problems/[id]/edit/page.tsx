import { AdminProblemEditPage } from "@/views/admin-problem-edit";

export default function Page(props: { params: Promise<{ id: string }> }) {
  return <AdminProblemEditPage {...props} />;
}
