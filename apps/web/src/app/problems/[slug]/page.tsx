import { ProblemDetailPage } from "@/views/problem-detail";

export default function Page(props: { params: Promise<{ slug: string }> }) {
  return <ProblemDetailPage {...props} />;
}
