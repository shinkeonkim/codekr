package worker

/*
실행 큐 사이의 순서를 정하는 규칙 (#639).

**채점기가 이미 같은 문제를 풀었다** (`apps/judge/internal/worker/priority.go`, #102).
규칙을 그대로 옮긴다 — 같은 문제에 다른 답을 두면 어느 쪽이 맞는지를 나중에 다시
따져야 하고, 두 곳이 갈라졌을 때 그것을 알아챌 방법이 없다.

핵심 문제는 **굶주림**이다. 대회 큐를 늘 먼저 읽으면, 대회가 도는 동안 평소 제출은
영원히 돌지 않는다. 대회는 몇 시간씩 이어지므로 "잠깐이면 괜찮다" 가 성립하지 않는다.

"가끔 순서를 뒤집는다" 를 고른 이유도 그쪽과 같다: 대기 시간을 추적하지 않아도 되고,
실행기가 여럿이어도 전체적으로 같은 비율이 나온다.
*/

/*
starvationInterval 은 몇 번에 한 번 낮은 쪽부터 읽을지 정한다.

**채점기(10)보다 크게 잡는다.** 이 값이 곧 "대회가 얼마나 우대받는가" 이고, 실행 큐는
대회가 실제로 밀리던 자리다 — 실측으로 조용할 때 14~17초가 방해가 있을 때 27~28초였다.
20 이면 평소 제출도 20번에 한 번은 먼저 읽히므로 완전히 멈추지는 않는다.
*/
const starvationInterval = 20

// streamOrder 는 이번 차례에 어떤 순서로 스트림을 시도할지 돌려준다.
//
// cycle 이 [starvationInterval] 의 배수일 때만 순서를 뒤집는다. 그 외에는 대회부터다.
func streamOrder(streams []string, cycle int) []string {
	if cycle > 0 && cycle%starvationInterval == 0 {
		return reversed(streams)
	}
	return streams
}

func reversed(values []string) []string {
	out := make([]string, len(values))
	for i, value := range values {
		out[len(values)-1-i] = value
	}
	return out
}
