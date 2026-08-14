package contract

import (
	"encoding/csv"
	"sort"
	"strconv"
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
//
// # 왜 CSV 로 읽는가 (#532)
//
// 전에는 하네스가 `-tA -F'|'` 로 냈고 여기서 줄을 그대로 견줬다. 그러면 **값에 든
// 구분자와 진짜 구분자를 가릴 수 없다** — `2|a|b||부산` 이 `a|b` 인지 `a`,`b` 인지
// 알 방법이 없어, 맞은 답이 틀렸다고 나오거나 다른 결과가 같다고 나올 수 있었다.
//
// 이제 하네스가 CSV 로 낸다. 여기서 **칸 단위로 읽어** 견주므로 값에 `,` 든 `|` 든
// 줄바꿈이든 들어 있어도 갈리지 않는다.
//
// 읽지 못하는 줄은 **버리지 않고 있는 그대로 한 칸짜리 행으로** 둔다. 판정을 못 하는
// 것보다 낫고, 옛 형식으로 낸 출력도 그대로 견줘진다.
func NormalizeSQLRows(block string, ignoreRowOrder bool) string {
	rows := canonicalSQLRows(block)
	if ignoreRowOrder {
		sort.Strings(rows)
	}
	return strings.Join(rows, "\n")
}

// canonicalSQLRows 는 CSV 한 줄을 "칸의 목록" 으로 읽어 다시 한 줄로 적는다.
//
// 다시 적을 때 [strconv.Quote] 를 쓰는 이유: 칸 안의 무엇도 칸 경계로 오해되지 않게
// 하려는 것이다. 이 문자열은 사람에게 보이지 않고 **견주기 위해서만** 쓰인다.
func canonicalSQLRows(block string) []string {
	reader := csv.NewReader(strings.NewReader(block))
	// 열 수가 줄마다 달라도 읽는다 — 그것을 막는 것은 여기가 할 일이 아니다.
	reader.FieldsPerRecord = -1
	reader.LazyQuotes = true

	records, err := reader.ReadAll()
	if err != nil {
		return rawSQLRows(block)
	}

	rows := make([]string, 0, len(records))
	for _, record := range records {
		if len(record) == 1 && strings.TrimSpace(record[0]) == "" {
			continue
		}
		quoted := make([]string, len(record))
		for i, field := range record {
			quoted[i] = strconv.Quote(strings.TrimRight(field, " \t\r"))
		}
		rows = append(rows, strings.Join(quoted, ","))
	}
	return rows
}

// rawSQLRows 는 CSV 로 읽히지 않을 때의 대비다. 줄 단위로 다듬기만 한다.
func rawSQLRows(block string) []string {
	rows := make([]string, 0)
	for _, line := range strings.Split(block, "\n") {
		trimmed := strings.TrimRight(line, " \t\r")
		if trimmed == "" {
			continue
		}
		rows = append(rows, strconv.Quote(trimmed))
	}
	return rows
}
