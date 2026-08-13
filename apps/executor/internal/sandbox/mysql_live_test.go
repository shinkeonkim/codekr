package sandbox

import (
	"context"
	"strings"
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// MySQL 은 타입 표기가 PostgreSQL 과 다르다 — `text` 대신 길이를 갖는 `varchar` 다.
const mysqlSchema = `
CREATE TABLE members (id int, name varchar(20), city varchar(20));
INSERT INTO members VALUES (1,'가','서울'),(2,'나','부산'),(3,'다','서울');
`

/*
MySQL 문제의 격리와 권한 (#454).

**PostgreSQL 의 목록을 그대로 옮기지 않는다.** 위험한 것이 DB 마다 다르기 때문이다 —
`COPY … FROM PROGRAM`·`pg_read_file` 자리에 MySQL 은 `LOAD_FILE()`·`INTO OUTFILE`·
UDF 설치·`SET GLOBAL` 이 있다. 그것을 찾아서 막지 않으면 **새 DB 가 샌드박스의 구멍**이 된다.

**권한 모델도 다르다.** MySQL 에는 `default_transaction_read_only` 같은 스위치가 없어
`주지 않는 것이 곧 막는 것`이다.
*/
func TestLiveMySQLSubmissionCannotEscapeReadOnly(t *testing.T) {
	box := newLiveSandbox(t)

	cases := []struct {
		name     string
		query    string
		expected string
	}{
		{"데이터 변경", "DELETE FROM members;", "DELETE command denied"},
		{"테이블 생성", "CREATE TABLE evil(x int);", "CREATE command denied"},
		{"스키마 변경", "DROP TABLE members;", "DROP command denied"},
		// FILE 권한이 없으면 파일로 새어 나가지 못한다.
		{"파일 쓰기", "SELECT * FROM members INTO OUTFILE '/work/leak.txt';", "FILE privilege"},
		// 비밀번호 해시가 있는 표. PostgreSQL 의 pg_authid 에 해당한다.
		{"계정 표 읽기", "SELECT user FROM mysql.user;", "SELECT command denied"},
		// UDF 로 호스트 명령을 부르려면 mysql 데이터베이스에 쓸 수 있어야 한다.
		{"UDF 설치", "CREATE FUNCTION sys_exec RETURNS INT SONAME 'lib_mysqludf_sys.so';", "Access denied"},
		{"전역 설정 변경", "SET GLOBAL general_log = ON;", "SYSTEM_VARIABLES_ADMIN"},
	}

	for _, testcase := range cases {
		t.Run(testcase.name, func(t *testing.T) {
			// **나란히 돌리지 않는다.** mysqld 는 뜨는 데만 3초를 쓰고 메모리도 넉넉히
			// 잡는다 — 일곱 개를 동시에 띄우면 서로 밀려 기동이 30초를 넘긴다.
			// 실제로 그랬다: 로컬에서 두 건이 시간 초과로 실패했고, CI 러너는 2코어다.
			outcome := runMySQL(t, box, testcase.query)

			if !strings.Contains(outcome.Stderr, testcase.expected) {
				t.Fatalf("막혀야 할 쿼리가 막히지 않았습니다.\nstdout=%q\nstderr=%q",
					outcome.Stdout, outcome.Stderr)
			}
		})
	}
}

/*
막히지 않지만 **아무것도 주지 않는** 것들 (#454).

오류가 나지 않으므로 위의 표에 넣을 수 없다. 그렇다고 확인하지 않으면 "막힌 줄 알았는데
값이 나왔다" 를 놓친다 — 실제로 `LOAD_FILE` 은 권한이 없을 때 오류 대신 `NULL` 을 준다.
*/
func TestLiveMySQLLeaksNothingThroughAllowedCalls(t *testing.T) {
	box := newLiveSandbox(t)

	t.Run("파일 읽기는 NULL 이다", func(t *testing.T) {
		outcome := runMySQL(t, box, "SELECT LOAD_FILE('/etc/passwd');")

		if strings.Contains(outcome.Stdout, "root:") {
			t.Fatalf("파일 내용이 새어 나왔습니다: %q", outcome.Stdout)
		}
	})

	t.Run("보이는 세션은 자기 것뿐이다", func(t *testing.T) {
		outcome := runMySQL(t, box, "SHOW PROCESSLIST;")

		// PROCESS 권한이 없으면 root 의 세션은 보이지 않는다.
		if strings.Contains(outcome.Stdout, "|root|") {
			t.Fatalf("다른 계정의 세션이 보입니다: %q", outcome.Stdout)
		}
	})
}

// 채점기는 DB 를 모른 채 결과를 견준다 — 그러려면 출력 형식이 PostgreSQL 판과 같아야 한다.
func TestLiveMySQLProducesTheSameShapeAsPostgres(t *testing.T) {
	box := newLiveSandbox(t)

	outcome := runMySQL(t, box, "SELECT city, count(*) FROM members GROUP BY city ORDER BY city;")

	expected, actual, found := contract.SplitSQLResults(outcome.Stdout)
	if !found {
		t.Fatalf("정답/제출 결과가 나뉘어 나오지 않았습니다: %q (stderr=%q)", outcome.Stdout, outcome.Stderr)
	}
	if contract.NormalizeSQLRows(expected, true) != contract.NormalizeSQLRows(actual, true) {
		t.Fatalf("같은 쿼리인데 결과가 다릅니다.\n정답=%q\n제출=%q", expected, actual)
	}
	if !strings.Contains(expected, "서울|2") {
		t.Fatalf("PostgreSQL 판과 같은 구분자여야 합니다: %q", expected)
	}
}

func runMySQL(t *testing.T, box Sandbox, query string) Outcome {
	t.Helper()
	outcome, err := box.Run(context.Background(), Spec{
		Image:      "mysql:8.4",
		SourceFile: "query.sql",
		SourceCode: query,
		Harness:    "mysql",
		User:       "999:999",
		Run:        []string{"sh", "run-mysql.sh"},
		ExtraFiles: map[string]string{
			"schema.sql": mysqlSchema,
			"answer.sql": "SELECT city, count(*) FROM members GROUP BY city ORDER BY city;",
		},
		// mysqld 초기화가 PostgreSQL 보다 느리다 (실측 약 2초).
		TimeLimitMs:    30000,
		MemoryLimitMb:  1024,
		MaxOutputBytes: 65536,
	})
	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	return outcome
}
