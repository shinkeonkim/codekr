import { ProblemSolvePage } from "@/views/problem-solve";

export default function Page(props: { params: Promise<{ slug: string }> }) {
  return <ProblemSolvePage {...props} />;
}
