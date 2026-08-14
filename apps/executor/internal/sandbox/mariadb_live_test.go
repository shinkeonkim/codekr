package sandbox

import (
	"context"
	"strings"
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// MariaDB 는 타입 표기가 PostgreSQL 과 다르다 — `text` 대신 길이를 갖는 `varchar` 다.
const mariadbSchema = `
CREATE TABLE members (id int, name varchar(20), city varchar(20));
INSERT INTO members VALUES (1,'가','서울'),(2,'나','부산'),(3,'다','서울');
`

/*
MariaDB 문제의 격리와 권한 (#454).

**PostgreSQL 의 목록을 그대로 옮기지 않는다.** 위험한 것이 DB 마다 다르기 때문이다 —
`COPY … FROM PROGRAM`·`pg_read_file` 자리에 MariaDB 는 `LOAD_FILE()`·`INTO OUTFILE`·
UDF 설치·`SET GLOBAL` 이 있다. 그것을 찾아서 막지 않으면 **새 DB 가 샌드박스의 구멍**이 된다.

**권한 모델도 다르다.** MariaDB 에는 `default_transaction_read_only` 같은 스위치가 없어
`주지 않는 것이 곧 막는 것`이다.
*/
func TestLiveMariaDBSubmissionCannotEscapeReadOnly(t *testing.T) {
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
		// MariaDB 는 이 자리를 SUPER 로 말한다 (MySQL 은 SYSTEM_VARIABLES_ADMIN).
		{"전역 설정 변경", "SET GLOBAL general_log = ON;", "SUPER privilege"},
	}

	for _, testcase := range cases {
		t.Run(testcase.name, func(t *testing.T) {
			// **나란히 돌리지 않는다.** 서버는 뜨는 데만 3초 넘게 쓰고 메모리도 넉넉히
			// 잡는다 — 일곱 개를 동시에 띄우면 서로 밀려 기동이 30초를 넘긴다.
			// 실제로 그랬다: 로컬에서 두 건이 시간 초과로 실패했고, CI 러너는 2코어다.
			outcome := runMariaDB(t, box, testcase.query)

			if !strings.Contains(outcome.Stderr, testcase.expected) {
				// 하네스 자체가 죽은 경우와 구분해서 알린다 — 둘 다 "막히지 않았다" 로
				// 보이지만 고칠 곳이 다르다.
				t.Fatalf("막혀야 할 쿼리가 막히지 않았습니다.\noutcome=%+v", outcome)
			}
		})
	}
}

/*
막히지 않지만 **아무것도 주지 않는** 것들 (#454).

오류가 나지 않으므로 위의 표에 넣을 수 없다. 그렇다고 확인하지 않으면 "막힌 줄 알았는데
값이 나왔다" 를 놓친다 — 실제로 `LOAD_FILE` 은 권한이 없을 때 오류 대신 `NULL` 을 준다.
*/
func TestLiveMariaDBLeaksNothingThroughAllowedCalls(t *testing.T) {
	box := newLiveSandbox(t)

	t.Run("파일 읽기는 NULL 이다", func(t *testing.T) {
		outcome := runMariaDB(t, box, "SELECT LOAD_FILE('/etc/passwd');")

		if strings.Contains(outcome.Stdout, "root:") {
			t.Fatalf("파일 내용이 새어 나왔습니다: %q", outcome.Stdout)
		}
	})

	t.Run("보이는 세션은 자기 것뿐이다", func(t *testing.T) {
		outcome := runMariaDB(t, box, "SHOW PROCESSLIST;")

		// PROCESS 권한이 없으면 root 의 세션은 보이지 않는다.
		if strings.Contains(outcome.Stdout, "|root|") {
			t.Fatalf("다른 계정의 세션이 보입니다: %q", outcome.Stdout)
		}
	})
}

// 채점기는 DB 를 모른 채 결과를 견준다 — 그러려면 출력 형식이 PostgreSQL 판과 같아야 한다.
func TestLiveMariaDBProducesTheSameShapeAsPostgres(t *testing.T) {
	box := newLiveSandbox(t)

	outcome := runMariaDB(t, box, "SELECT city, count(*) FROM members GROUP BY city ORDER BY city;")

	expected, actual, found := contract.SplitSQLResults(outcome.Stdout)
	if !found {
		t.Fatalf("정답/제출 결과가 나뉘어 나오지 않았습니다: %q (stderr=%q)", outcome.Stdout, outcome.Stderr)
	}
	if contract.NormalizeSQLRows(expected, true) != contract.NormalizeSQLRows(actual, true) {
		t.Fatalf("같은 쿼리인데 결과가 다릅니다.\n정답=%q\n제출=%q", expected, actual)
	}
	/*
	  **PostgreSQL 판과 같은 형식이어야 한다** — 채점기는 DB 를 모른다.

	  둘 다 CSV 다 (#532). 다만 감싸는 방식은 다를 수 있다: psql 은 필요할 때만
	  감싸고, 이쪽은 awk 가 모든 칸을 감싼다. **채점기는 CSV 로 읽으므로 그 차이가
	  판정에 닿지 않는다** — 그래서 여기서도 정규화한 뒤 견준다.
	*/
	if contract.NormalizeSQLRows(expected, true) != contract.NormalizeSQLRows("\"서울\",\"2\"\n\"부산\",\"1\"\n", true) {
		t.Fatalf("PostgreSQL 판과 같은 형식이어야 합니다: %q", expected)
	}
}

func runMariaDB(t *testing.T, box Sandbox, query string) Outcome {
	t.Helper()
	outcome, err := box.Run(context.Background(), Spec{
		Image:      "mariadb:11",
		SourceFile: "query.sql",
		SourceCode: query,
		Harness:    "mariadb",
		User:       "999:999",
		Run:        []string{"sh", "run-mariadb.sh"},
		ExtraFiles: map[string]string{
			"schema.sql": mariadbSchema,
			"answer.sql": "SELECT city, count(*) FROM members GROUP BY city ORDER BY city;",
		},
		// 초기화가 PostgreSQL 보다 느리다 (실측 약 3.5초).
		TimeLimitMs:    30000,
		MemoryLimitMb:  1024,
		MaxOutputBytes: 65536,
	})
	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	return outcome
}
