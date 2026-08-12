// Package judging 은 채점 워크플로를 담는다 — 실행 결과를 판정으로 바꾸고 집계한다.
package judging

import (
	"math"
	"strconv"
	"strings"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// OutputMatches 는 프로그램 출력이 기대 출력과 같은지 판단한다.
//
// 규칙(docs/02_도메인_모델.md 3장):
//   - 각 줄의 오른쪽 공백은 무시한다
//   - 끝의 빈 줄은 무시한다
//   - 그 외에는 정확히 일치해야 한다
//
// 실수 답 문제는 이것으로 낼 수 없다 — `3.5` 와 `3.500000` 이 다른 답이 되고,
// 그 차이는 **푸는 사람의 실력이 아니라 언어의 기본 출력 자릿수**에서 온다.
// 그 경우에는 [OutputMatchesWithin] 을 쓴다 (#279, ADR-0010).
func OutputMatches(actual, expected string) bool {
	return normalize(actual) == normalize(expected)
}

// OutputMatchesWithin 은 비교 방식에 따라 정확 일치 또는 오차 비교를 한다.
//
// **정확 일치 경로는 손대지 않았다.** 기존 문제의 판정이 하나라도 바뀌면 안 되고,
// 빈 방식은 정확 일치로 읽는다.
func OutputMatchesWithin(actual, expected, comparison string, epsilon float64) bool {
	if comparison != contract.CompareFloat {
		return OutputMatches(actual, expected)
	}
	return tokensMatch(normalize(actual), normalize(expected), epsilon)
}

/*
tokensMatch 는 공백으로 쪼갠 토큰을 하나씩 견준다.

**줄 전체를 실수로 읽지 않는다.** 출력이 실수 하나가 아니라 여러 개일 수 있고,
숫자와 글자가 섞인 줄도 있다("Case 1: 3.5"). 토큰마다 숫자로 읽히면 숫자로,
아니면 글자로 비교한다.

토큰 **개수가 다르면 틀린 답**이다. 개수를 맞추지 않으면 답을 덜 낸 제출이 통과한다.
*/
func tokensMatch(actual, expected string, epsilon float64) bool {
	actualTokens := strings.Fields(actual)
	expectedTokens := strings.Fields(expected)
	if len(actualTokens) != len(expectedTokens) {
		return false
	}

	for i := range expectedTokens {
		if !tokenMatches(actualTokens[i], expectedTokens[i], epsilon) {
			return false
		}
	}
	return true
}

func tokenMatches(actual, expected string, epsilon float64) bool {
	if actual == expected {
		return true
	}

	expectedValue, expectedOK := parseFinite(expected)
	actualValue, actualOK := parseFinite(actual)
	// 한쪽만 숫자면 다른 것이다. 둘 다 숫자가 아니면 위의 글자 비교에서 이미 갈렸다.
	if !expectedOK || !actualOK {
		return false
	}

	return withinEpsilon(actualValue, expectedValue, epsilon)
}

/*
parseFinite 는 유한한 실수만 숫자로 인정한다.

`nan`·`inf` 는 숫자로 읽지 않는다 — `NaN` 은 자기 자신과도 같지 않아 오차 비교가
뜻을 잃고, `inf` 는 차이가 늘 무한이다. 그런 출력은 **글자 그대로 같을 때만** 맞다.

지수 표기(`3.5e0`)는 받는다. 언어의 기본 출력이 그 모양인 경우가 있고, 그것을
막으면 이 이슈가 풀려던 "언어로 갈리는" 문제가 다시 생긴다.
*/
func parseFinite(token string) (float64, bool) {
	value, err := strconv.ParseFloat(token, 64)
	if err != nil || math.IsNaN(value) || math.IsInf(value, 0) {
		return 0, false
	}
	return value, true
}

/*
withinEpsilon 은 절대 오차와 상대 오차 중 **하나만 만족해도** 맞다고 본다.

답이 `0.0000001` 일 때와 `1000000.5` 일 때 같은 절대 오차를 쓸 수 없다. 큰 수에서는
상대 오차가, 0 근처에서는 절대 오차가 맞는 잣대다 — 관례대로 둘을 함께 본다.
*/
func withinEpsilon(actual, expected, epsilon float64) bool {
	diff := math.Abs(actual - expected)
	if diff <= epsilon {
		return true
	}
	scale := math.Abs(expected)
	return scale > 0 && diff/scale <= epsilon
}

func normalize(text string) string {
	lines := strings.Split(strings.ReplaceAll(text, "\r\n", "\n"), "\n")
	for i, line := range lines {
		lines[i] = strings.TrimRight(line, " \t")
	}
	// 끝의 빈 줄을 걷어낸다.
	end := len(lines)
	for end > 0 && lines[end-1] == "" {
		end--
	}
	return strings.Join(lines[:end], "\n")
}
