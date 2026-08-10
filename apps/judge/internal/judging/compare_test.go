package judging

import "testing"

func TestOutputMatchesIgnoresTrailingWhitespaceAndBlankLines(t *testing.T) {
	cases := []struct {
		actual   string
		expected string
		match    bool
		name     string
	}{
		{"3\n", "3\n", true, "완전히 같음"},
		{"3", "3\n", true, "끝 개행 차이"},
		{"3\n\n\n", "3\n", true, "끝의 빈 줄"},
		{"3   \n", "3\n", true, "줄 끝 공백"},
		{"1\n2\n", "1\n2\n", true, "여러 줄"},
		{"1\r\n2\r\n", "1\n2\n", true, "CRLF 개행"},
		{"3\n", "4\n", false, "값이 다름"},
		{" 3\n", "3\n", false, "앞 공백은 의미가 있다"},
		{"1\n2\n", "2\n1\n", false, "순서가 다름"},
		{"", "0\n", false, "출력이 없음"},
	}

	for _, c := range cases {
		if got := OutputMatches(c.actual, c.expected); got != c.match {
			t.Errorf("%s: OutputMatches(%q, %q) = %v", c.name, c.actual, c.expected, got)
		}
	}
}
