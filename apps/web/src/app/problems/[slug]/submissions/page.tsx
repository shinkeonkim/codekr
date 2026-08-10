import { ProblemSubmissionsPage } from "@/views/problem-submissions";

export default function Page(props: { params: Promise<{ slug: string }> }) {
  return <ProblemSubmissionsPage {...props} />;
}
