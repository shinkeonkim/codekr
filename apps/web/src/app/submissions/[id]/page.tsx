import { SubmissionDetailPage } from "@/views/submission-detail";

export default function Page(props: { params: Promise<{ id: string }> }) {
  return <SubmissionDetailPage {...props} />;
}
