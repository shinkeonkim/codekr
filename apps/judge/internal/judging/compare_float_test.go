package judging

import (
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// 실수 답 문제에서 언어별 출력 차이가 판정을 가르지 않아야 한다 (#279, ADR-0010).
func TestOutputMatchesWithinAcceptsFloatFormatsAndError(t *testing.T) {
	const eps = 1e-6

	cases := []struct {
		name     string
		actual   string
		expected string
		match    bool
	}{
		// 이슈가 든 세 가지 — 같은 답을 언어마다 다르게 찍는다.
		{"자릿수가 달라도 같은 값", "3.500000\n", "3.5\n", true},
		{"부동소수점 오차", "3.4999999999999996\n", "3.5\n", true},
		{"지수 표기", "3.5e0\n", "3.5\n", true},
		{"정수로 찍어도 값이 같으면", "4\n", "4.0\n", true},

		{"오차를 넘으면 틀린 답", "3.6\n", "3.5\n", false},
		{"부호가 다르면 틀린 답", "-3.5\n", "3.5\n", false},

		// 큰 수에서는 상대 오차가 잣대다 — 같은 절대 오차를 쓸 수 없다.
		{"큰 수의 상대 오차", "1000000.4999999\n", "1000000.5\n", true},

		// 여러 값·글자 섞임.
		{"토큰마다 견준다", "1.0 2.5 x\n", "1 2.5 x\n", true},
		{"토큰 개수가 다르면 틀린 답", "1.0 2.5\n", "1 2.5 3\n", false},
		{"글자는 그대로 맞아야 한다", "1.0 y\n", "1 x\n", false},

		// NaN·Inf 는 숫자로 읽지 않는다 — 오차 비교가 뜻을 잃는다.
		{"NaN 은 글자로만 같다", "NaN\n", "NaN\n", true},
		{"NaN 과 숫자는 다르다", "NaN\n", "3.5\n", false},
		{"Inf 는 글자로만 같다", "+Inf\n", "3.5\n", false},

		{"빈 출력끼리는 같다", "", "", true},
		{"빈 출력과 값은 다르다", "", "3.5\n", false},
	}

	for _, c := range cases {
		if got := OutputMatchesWithin(c.actual, c.expected, contract.CompareFloat, eps); got != c.match {
			t.Errorf("%s: OutputMatchesWithin(%q, %q) = %v, 기대 %v", c.name, c.actual, c.expected, got, c.match)
		}
	}
}

// **기존 문제의 판정이 하나도 바뀌면 안 된다** — 빈 방식은 정확 일치다.
func TestOutputMatchesWithinKeepsExactPathUntouched(t *testing.T) {
	cases := []struct {
		comparison string
		match      bool
	}{
		{contract.CompareExact, false},
		{"", false}, // 이 필드가 없던 시절의 작업
		{contract.CompareFloat, true},
	}

	for _, c := range cases {
		got := OutputMatchesWithin("3.500000\n", "3.5\n", c.comparison, 1e-6)
		if got != c.match {
			t.Errorf("비교 방식 %q: %v, 기대 %v", c.comparison, got, c.match)
		}
	}
}

// 오차를 0 으로 두면 값이 정확히 같아야 한다 — 어드민이 실수로 비워도 느슨해지지 않는다.
func TestOutputMatchesWithinZeroEpsilonStillComparesNumerically(t *testing.T) {
	if !OutputMatchesWithin("3.50\n", "3.5\n", contract.CompareFloat, 0) {
		t.Error("같은 값은 표기가 달라도 통과해야 합니다")
	}
	if OutputMatchesWithin("3.5000001\n", "3.5\n", contract.CompareFloat, 0) {
		t.Error("오차 0 에서는 다른 값이 통과하면 안 됩니다")
	}
}
