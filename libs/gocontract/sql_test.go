package contract

import (
	"strings"
	"testing"
)

// 값에 든 구분자가 진짜 구분자와 갈리지 않던 문제 (#532).
//
// 전에는 하네스가 `-tA -F'|'` 로 냈고 줄을 그대로 견줬다. 그러면 `a|b` 한 칸과
// `a`,`b` 두 칸이 **같은 줄**이 되어, 맞은 답이 틀렸다고 나오거나 그 반대가 됐다.
func TestNormalizeSQLRowsSeparatesEmbeddedDelimiter(t *testing.T) {
	/*
		옛 형식(`-tA -F'|'`)에서는 아래 두 입력이 **똑같이** `a|b|부산` 한 줄이었다.
		그래서 한 칸짜리 `a|b` 와 두 칸짜리 `a`,`b` 를 가릴 수 없었다.

		**여기 그것을 확인한다던 줄이 있었는데 자기 자신을 견주고 있었다** (#668):
		`NormalizeSQLRows(x) != NormalizeSQLRows(x)`. 무엇을 돌려주든 통과한다.
		확인해야 할 것은 아래 세 줄이 이미 하고 있으므로 지웠다.
	*/
	oneField := NormalizeSQLRows("\"a|b\",\"부산\"\n", false)
	twoFields := NormalizeSQLRows("\"a\",\"b\",\"부산\"\n", false)

	// 이제는 칸으로 읽으므로 갈린다. 칸 수까지 확인한다 — 문자열이 다른 것만으로는
	// 파싱이 됐다는 증거가 안 된다.
	if got, want := strings.Count(oneField, ",")+1, 2; got != want {
		t.Fatalf("`a|b` 가 한 칸이어야 한다. 칸 수=%d, 값=%q", got, oneField)
	}
	if got, want := strings.Count(twoFields, ",")+1, 3; got != want {
		t.Fatalf("`a`,`b` 가 두 칸이어야 한다. 칸 수=%d, 값=%q", got, twoFields)
	}
	if oneField == twoFields {
		t.Fatalf("칸 수가 다른 두 결과가 같다고 나온다: %q", oneField)
	}
}

func TestNormalizeSQLRowsSeparatesEmbeddedComma(t *testing.T) {
	// CSV 로 옮겼으므로 이제는 쉼표가 그 자리에 있다.
	oneField := NormalizeSQLRows("\"a,b\"\n", false)
	twoFields := NormalizeSQLRows("\"a\",\"b\"\n", false)

	if oneField == twoFields {
		t.Fatalf("쉼표가 든 값과 칸 둘이 같다고 나온다: %q", oneField)
	}
}

// 줄바꿈이 든 값도 한 행이다. 줄 단위로 갈랐다면 여기서 두 행이 됐다.
func TestNormalizeSQLRowsKeepsEmbeddedNewline(t *testing.T) {
	withNewline := NormalizeSQLRows("\"첫 줄\n둘째 줄\",\"x\"\n", false)
	twoRows := NormalizeSQLRows("\"첫 줄\",\"x\"\n\"둘째 줄\",\"x\"\n", false)

	if withNewline == twoRows {
		t.Fatalf("줄바꿈이 든 값이 두 행으로 갈렸다: %q", withNewline)
	}
}

func TestNormalizeSQLRowsIgnoresRowOrderWhenAsked(t *testing.T) {
	first := NormalizeSQLRows("\"1\",\"가\"\n\"2\",\"나\"\n", true)
	second := NormalizeSQLRows("\"2\",\"나\"\n\"1\",\"가\"\n", true)

	if first != second {
		t.Fatalf("행 순서를 무시하기로 했는데 다르다:\n%q\n%q", first, second)
	}
}

func TestNormalizeSQLRowsKeepsRowOrderWhenNotAsked(t *testing.T) {
	first := NormalizeSQLRows("\"1\",\"가\"\n\"2\",\"나\"\n", false)
	second := NormalizeSQLRows("\"2\",\"나\"\n\"1\",\"가\"\n", false)

	if first == second {
		t.Fatal("행 순서를 봐야 하는데 같다고 나온다")
	}
}

// 빈 줄과 꼬리 공백은 psql 출력의 부수적인 차이다 — 그것으로 오답을 주면 안 된다.
func TestNormalizeSQLRowsIgnoresBlankLines(t *testing.T) {
	padded := NormalizeSQLRows("\n\"1\",\"가\"\n\n", false)
	plain := NormalizeSQLRows("\"1\",\"가\"\n", false)

	if padded != plain {
		t.Fatalf("빈 줄이 결과를 바꾼다:\n%q\n%q", padded, plain)
	}
}

// NULL 과 빈 글자는 다르다. 하네스가 NULL 을 `∅` 로 적어 보내는 이유다.
func TestNormalizeSQLRowsSeparatesNullFromEmpty(t *testing.T) {
	null := NormalizeSQLRows("\"∅\"\n", false)
	empty := NormalizeSQLRows("\"\"\n", false)

	if null == empty {
		t.Fatal("NULL 과 빈 글자가 같다고 나온다")
	}
}

// 옛 형식(파이프)으로 낸 출력도 견줘지긴 해야 한다 — 판정을 못 하는 것보다 낫다.
func TestNormalizeSQLRowsFallsBackForOldFormat(t *testing.T) {
	same := NormalizeSQLRows("1|가\n", false)
	other := NormalizeSQLRows("1|나\n", false)

	if same == "" {
		t.Fatal("옛 형식이 통째로 버려졌다")
	}
	if same == other {
		t.Fatal("다른 줄이 같다고 나온다")
	}
}

func TestSplitSQLResults(t *testing.T) {
	stdout := "잡음\n" + SQLExpectedMarker + "\n\"1\"\n" + SQLActualMarker + "\n\"2\"\n"

	expected, actual, found := SplitSQLResults(stdout)
	if !found {
		t.Fatal("표식을 찾지 못했다")
	}
	if NormalizeSQLRows(expected, false) == NormalizeSQLRows(actual, false) {
		t.Fatal("정답과 제출이 같다고 나온다")
	}
}
