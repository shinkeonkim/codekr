import { TermDetailPage } from "@/views/terms";

export const metadata = { title: "약관" };

export default function Page(props: { params: Promise<{ id: string }> }) {
  return <TermDetailPage {...props} />;
}
