package sandbox

import (
	"context"
	"strings"
	"testing"
)

const sqlSchema = `
CREATE TABLE members (id int, name text, city text);
INSERT INTO members VALUES (1,'가','서울'),(2,'나','부산'),(3,'다','서울');
`

// SQL 문제의 격리와 권한 (#60).
//
// **쓰기 차단은 문자열 필터가 아니라 권한으로 한다.** 필터는 우회되지만 권한은
// 우회되지 않는다 — 이 시험이 그것을 확인한다.
func TestLiveSQLSubmissionCannotEscapeReadOnly(t *testing.T) {
	box := newLiveSandbox(t)

	cases := []struct {
		name     string
		query    string
		expected string
	}{
		{"스키마 변경", "DROP TABLE members;", "read-only transaction"},
		{"테이블 생성", "CREATE TABLE evil(x int);", "read-only transaction"},
		{"데이터 변경", "DELETE FROM members;", "read-only transaction"},
		// 슈퍼유저였다면 호스트 명령을 실행할 수 있다. 제출 롤은 슈퍼유저가 아니다.
		{"명령 실행", "COPY members FROM PROGRAM 'id';", "pg_execute_server_program"},
		{"파일 읽기", "SELECT pg_read_file('/etc/passwd');", "permission denied"},
		{"비밀번호 해시 읽기", "SELECT rolname FROM pg_authid;", "permission denied"},
	}

	for _, testcase := range cases {
		t.Run(testcase.name, func(t *testing.T) {
			t.Parallel()
			outcome := runSQL(t, box, testcase.query)

			if !strings.Contains(outcome.Stderr, testcase.expected) {
				t.Fatalf("막혀야 할 쿼리가 막히지 않았습니다.\nstdout=%q\nstderr=%q",
					outcome.Stdout, outcome.Stderr)
			}
		})
	}
}

func TestLiveSQLComparesAgainstAnswerQuery(t *testing.T) {
	box := newLiveSandbox(t)

	outcome := runSQL(t, box, "SELECT city, count(*) FROM members GROUP BY city ORDER BY city;")

	// 정답 쿼리와 제출 쿼리의 결과를 구분자로 나눠 함께 돌려준다.
	// 정답을 결과 집합이 아니라 쿼리로 두면 시드가 바뀔 때 기대 결과도 따라간다.
	expected, actual, found := strings.Cut(outcome.Stdout, "--- codekr:actual")
	if !found {
		t.Fatalf("정답/제출 결과가 나뉘어 나오지 않았습니다: %q (stderr=%q)", outcome.Stdout, outcome.Stderr)
	}
	expected = strings.TrimSpace(strings.TrimPrefix(strings.TrimSpace(expected), "--- codekr:expected"))
	if strings.TrimSpace(actual) != expected {
		t.Fatalf("같은 쿼리인데 결과가 다릅니다.\n정답=%q\n제출=%q", expected, strings.TrimSpace(actual))
	}
	if !strings.Contains(expected, "서울|2") {
		t.Fatalf("시드 데이터가 반영되지 않았습니다: %q", expected)
	}
}

// 컨테이너가 끝나면 데이터베이스도 사라진다. 제출끼리 같은 인스턴스를 나눠 쓰지 않는다.
func TestLiveSQLGetsFreshDatabaseEachRun(t *testing.T) {
	box := newLiveSandbox(t)

	first := runSQL(t, box, "SELECT count(*) FROM members;")
	second := runSQL(t, box, "SELECT count(*) FROM members;")

	if !strings.Contains(first.Stdout, "3") || !strings.Contains(second.Stdout, "3") {
		t.Fatalf("두 실행이 같은 초기 상태여야 합니다: %q / %q", first.Stdout, second.Stdout)
	}
}

func runSQL(t *testing.T, box Sandbox, query string) Outcome {
	t.Helper()
	outcome, err := box.Run(context.Background(), Spec{
		Image:      "postgres:16-alpine",
		SourceFile: "query.sql",
		SourceCode: query,
		Harness:    "sql",
		User:       "70:70",
		Run:        []string{"sh", "run-sql.sh"},
		ExtraFiles: map[string]string{
			"schema.sql": sqlSchema,
			"answer.sql": "SELECT city, count(*) FROM members GROUP BY city ORDER BY city;",
		},
		TimeLimitMs:    20000,
		MemoryLimitMb:  512,
		MaxOutputBytes: 65536,
	})
	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	return outcome
}
