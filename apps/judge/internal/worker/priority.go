package worker

/*
등급 사이의 순서를 정하는 규칙 (#102).

핵심 문제는 **굶주림**이다. 높은 등급을 늘 먼저 읽으면 낮은 등급은 영원히 돌지 않는다.
그래서 일정 횟수마다 한 번은 낮은 등급부터 읽는다.

"가끔 순서를 뒤집는다" 는 방식을 고른 이유는 대기 시간을 추적하지 않아도 되기 때문이다.
대기 시간 기반 승격은 정확하지만, 워커가 여럿이면 각자 다른 시각을 보게 되어
언제 승격됐는지가 흔들린다. 횟수는 워커마다 세도 전체적으로 같은 비율이 나온다.
*/

// starvationInterval 은 몇 번에 한 번 낮은 등급부터 읽을지 정한다.
//
// 10 이면 낮은 등급이 최소 10번에 한 번은 기회를 얻는다. 값을 키우면 높은 등급이
// 더 빨리 처리되고, 줄이면 낮은 등급이 덜 밀린다.
const starvationInterval = 10

// streamOrder 는 이번 차례에 어떤 순서로 스트림을 시도할지 돌려준다.
//
// cycle 이 starvationInterval 의 배수일 때만 순서를 뒤집는다. 그 외에는 높은 등급부터다.
// 스트림이 하나뿐인 차선(대회, #62)에서는 뒤집어도 같은 목록이라 아무 일도 없다.
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
