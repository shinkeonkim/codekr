package contract

import "context"

/*
LogKeySubmission 은 제출 하나를 세 앱의 로그에서 잇는 필드 이름이다 (#681).

한 번 제출하면 로그가 최소 세 곳에 남는다(api → judge → executor). 셋이 공통으로 들고
다니는 값이 없어서, "이 제출이 왜 3분 걸렸나" 를 보려면 시각으로 어림잡아 세 벌을
눈으로 맞춘다 — 동시에 열 건이 돌면 그것도 못 한다.

**W3C `traceparent` 를 만들지 않았다.** 두 가지 이유다.

첫째, **지금 홈랩에 트레이스 저장소가 없다.** 없는 것을 위해 지금 값을 치르게 된다.

둘째가 더 크다: **이 흐름은 동기 호출 트리가 아니다.** api 는 202 를 돌려주고 끝나고,
채점은 한참 뒤 다른 프로세스에서 시작한다. 트레이스 문맥은 호출 트리를 모델로 하므로
이 모양을 담으려면 span link 를 써야 하는데, 그것이 쓸모 있으려면 결국 저장소가 있어야
한다. **큐로 끊긴 흐름의 자연스러운 상관 키는 제출 번호 자체다.**

로그 필드 이름은 [LogKeySubmission] 하나로 통일한다 — api 의 MDC 키도 같다.
*/
const LogKeySubmission = "submissionId"

type submissionKey struct{}

/*
WithSubmissionID 는 채점 한 건 동안 따라다닐 제출 번호를 문맥에 싣는다.

**문맥에 두는 이유**: 실행 작업(`ExecJob`)을 만드는 곳이 유형마다 하나씩 아홉 군데다.
거기에 필드를 하나씩 더하면 **언젠가 한 유형이 빠지고, 빠진 줄은 아무도 모른다** —
그 유형의 로그만 조용히 안 이어진다. 발행하는 곳은 `dispatch.Executor.Run` 하나뿐이라
거기서 한 번 찍으면 아홉 군데가 손대지 않고도 따라온다.
*/
func WithSubmissionID(ctx context.Context, id int64) context.Context {
	return context.WithValue(ctx, submissionKey{}, id)
}

// SubmissionIDFrom 은 문맥에 실린 제출 번호를 돌려준다. 없으면 0 이다.
//
// 0 은 "제출과 무관한 실행" 이다 — 코드 실행기(#68)가 그렇다.
func SubmissionIDFrom(ctx context.Context) int64 {
	id, _ := ctx.Value(submissionKey{}).(int64)
	return id
}
