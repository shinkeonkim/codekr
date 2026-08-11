package contract

import (
	"sort"
	"strings"
)

// SQL 실행 하네스가 정답 결과와 제출 결과를 나누는 표식 (#60).
//
// **이 파일이 실행기(하네스 출력)와 채점기(비교) 사이의 계약이다.** 양쪽이 각자
// 문자열을 갖고 있으면, 한쪽을 고칠 때 다른 쪽이 조용히 어긋난다.
const (
	SQLExpectedMarker = "--- codekr:expected"
	SQLActualMarker   = "--- codekr:actual"
)

// SplitSQLResults 는 하네스 출력에서 정답 결과와 제출 결과를 갈라낸다.
//
// found 가 거짓이면 하네스가 정답 결과까지 내지 못한 것이다 — 스키마나 정답 쿼리가
// 잘못됐다는 뜻이고, 사용자 잘못이 아니다.
func SplitSQLResults(stdout string) (expected, actual string, found bool) {
	_, rest, hasExpected := strings.Cut(stdout, SQLExpectedMarker)
	if !hasExpected {
		return "", "", false
	}
	expected, actual, found = strings.Cut(rest, SQLActualMarker)
	return expected, actual, found
}

// NormalizeSQLRows 는 비교 전에 결과를 다듬는다.
//
// 꼬리 공백과 빈 줄은 항상 무시한다 — psql 출력의 부수적인 차이이고, 그것으로
// 오답을 주면 사용자는 무엇이 틀렸는지 알 수 없다.
func NormalizeSQLRows(block string, ignoreRowOrder bool) string {
	rows := make([]string, 0)
	for _, line := range strings.Split(block, "\n") {
		trimmed := strings.TrimRight(line, " \t\r")
		if trimmed == "" {
			continue
		}
		rows = append(rows, trimmed)
	}
	if ignoreRowOrder {
		sort.Strings(rows)
	}
	return strings.Join(rows, "\n")
}
