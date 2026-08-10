// Package judging 은 채점 워크플로를 담는다 — 실행 결과를 판정으로 바꾸고 집계한다.
package judging

import "strings"

// OutputMatches 는 프로그램 출력이 기대 출력과 같은지 판단한다.
//
// 규칙(docs/02_도메인_모델.md 3장):
//   - 각 줄의 오른쪽 공백은 무시한다
//   - 끝의 빈 줄은 무시한다
//   - 그 외에는 정확히 일치해야 한다
//
// 부동소수점 허용 오차 비교는 MVP 범위 밖이다 (ADR-0006).
func OutputMatches(actual, expected string) bool {
	return normalize(actual) == normalize(expected)
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
