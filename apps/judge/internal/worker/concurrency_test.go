package worker

import "testing"

/*
워커 수를 살아 있는 채 바꾼다 (#390).

**받은 값을 그대로 믿지 않는다.** 이 값은 어드민 화면에서 오고, 잘못 들어오면
그 차선의 채점이 통째로 멈추거나 노드가 죽는다.
*/
func TestClampConcurrency(t *testing.T) {
	base := 4

	cases := []struct {
		name  string
		value int
		want  int
		why   string
	}{
		{"그대로 쓰는 값", 6, 6, "범위 안이면 손대지 않는다"},
		{
			"0 은 허용하지 않는다", 0, 1,
			"0 이면 그 차선의 채점이 멈추는데, 화면에서 그것은 '적체' 로 보인다 — 원인이 조정이라는 것을 아무도 모른다",
		},
		{"음수도 마찬가지", -3, 1, "같은 이유"},
		{"상한을 넘으면 자른다", 1000, base * maxConcurrencyFactor, "실수로 큰 수를 넣어도 노드가 죽지 않아야 한다"},
		{"상한 그 자체", base * maxConcurrencyFactor, base * maxConcurrencyFactor, "경계는 허용한다"},
	}

	for _, each := range cases {
		t.Run(each.name, func(t *testing.T) {
			if got := clampConcurrency(each.value, base); got != each.want {
				t.Errorf("clampConcurrency(%d, %d) = %d, want %d — %s", each.value, base, got, each.want, each.why)
			}
		})
	}
}

// 기동 설정이 1 이어도 상한이 최소 1 은 되어야 한다.
func TestClampConcurrencyWithTinyBase(t *testing.T) {
	if got := clampConcurrency(5, 0); got < minConcurrency {
		t.Errorf("상한이 최소보다 작아졌습니다: %d", got)
	}
}
