# MySQL 문제 실행 하네스 (#454).
#
# `run-sql.sh`(PostgreSQL) 와 **같은 약속**을 지킨다. 채점기는 어느 DB 였는지 모른 채
# 결과를 견주므로, 이 파일이 바꿔도 되는 것은 **DB 를 다루는 방법**뿐이고 주고받는
# 파일 이름과 출력 형식은 그대로다.
#
# 작업 디렉터리에 있을 수 있는 파일:
#   schema.sql  (선택) 스키마와 시드. root 로 넣는다. 문제가 소유한다
#   answer.sql  (선택) 정답. 있으면 먼저 돌려 기대 결과를 만든다
#   verify.sql  (선택) 끝난 뒤의 상태를 읽는 쿼리 (#453)
#   allow-write (선택) 있으면 제출이 쓸 수 있다 (#453)
#   query.sql   제출. 기본은 **SELECT 권한만 가진 계정**으로 돈다
#
# ## PostgreSQL 과 다른 것
#
# **권한 모델이 다르다.** PostgreSQL 은 `default_transaction_read_only` 로 트랜잭션
# 자체를 읽기 전용으로 만들 수 있지만, MySQL 에는 그런 스위치가 없다. 그래서 여기서는
# **주지 않는 것이 곧 막는 것**이다 — solver 에게는 `SELECT` 만 준다.
#
# **위험한 것의 목록도 다르다.** PostgreSQL 의 `COPY … FROM PROGRAM`·`pg_read_file`
# 자리에 MySQL 은 `LOAD_FILE()`·`INTO OUTFILE`·`LOAD DATA INFILE`·`mysql.user`·
# UDF 설치·`SET GLOBAL` 이 있다. 그 각각을 막는 것이 아래의 서버 인자와 권한이다.
set -eu

DATADIR=/work/data
SOCK=/work/mysql.sock
export MYSQL_HOME=/work

# **디렉터리는 셸이 만든다.**
#
# mysqld 자신은 user namespace 재매핑 아래에서 이 자리에 디렉터리를 만들지 못한다
# (`errno 13`). 같은 컨테이너·같은 계정에서 셸의 `mkdir` 은 되는 것을 확인했으므로,
# 만드는 일만 셸이 맡는다. 비어 있는 디렉터리를 주면 mysqld 는 그대로 쓴다.
mkdir -p "$DATADIR"

# 기동에 실패하면 **왜 실패했는지 보여준다.** 로그를 컨테이너 안에만 두면 화면에는
# 빈 출력만 남고, 그것은 "쿼리가 아무 결과도 내지 않았다" 와 구분되지 않는다.
die() {
    echo "mysqld 를 띄우지 못했습니다: $1" >&2
    # 실패한 자리의 상태를 함께 남긴다. 로그만으로는 "권한이 없다" 가 누구의 권한인지
    # 알 수 없다 — 실행 계정과 디렉터리의 주인을 같이 봐야 한다.
    echo "실행 계정: $(id)" >&2
    ls -ld /work /work/data 2>&1 | sed 's/^/  /' >&2
    # **셸도 못 만드는지 본다.** 프로세스의 문제와 자리의 문제는 고칠 곳이 다르다.
    echo "  셸 mkdir: $(mkdir /work/probe.d 2>&1 && echo 됨)" >&2
    echo "  셸 touch: $(touch /work/probe.f 2>&1 && echo 됨)" >&2
    tail -30 /work/.my.log >&2 2>/dev/null || true
    exit 1
}

# --initialize-insecure: root 에 비밀번호가 없다. 네트워크가 없고 컨테이너와 함께
# 사라지는 인스턴스라 비밀번호는 지킬 것이 아니라 기동을 늦추는 것이다.
# **`--no-defaults` 로 이미지의 `/etc/my.cnf` 를 통째로 무시한다.**
#
# 그 파일에 `user=mysql` 이 들어 있다. 그러면 mysqld 는 우리가 `--user` 를 주지 않아도
# 권한을 내려놓는 절차(`initgroups`·`setgid`)를 밟는데, **능력을 모두 뗀 샌드박스**
# (특히 user namespace 재매핑 아래)에서는 그것이 실패해 데이터 디렉터리조차 만들지
# 못한다. 우리는 처음부터 그 계정으로 돌므로 내려놓을 권한이 없다.
#
# 이미지의 기본값을 안 쓰는 편이 낫기도 하다 — 그 파일이 바뀌면 채점 환경이 조용히
# 달라진다. 필요한 값은 전부 아래에 적혀 있다.
mysqld --no-defaults --initialize-insecure --datadir="$DATADIR" >/work/.my.log 2>&1 \
    || die "초기화 실패"

# --skip-networking : 유닉스 소켓만. 네트워크는 이미 꺼져 있지만 한 겹만 믿지 않는다
# --secure-file-priv=NULL : `INTO OUTFILE`·`LOAD DATA INFILE` 자체를 끈다
# --local-infile=0 : 클라이언트 쪽 파일 읽기를 끈다
# --max-execution-time : 쿼리 하나가 서버를 붙잡지 못하게 (SELECT 에 걸린다)
mysqld --no-defaults --datadir="$DATADIR" --socket="$SOCK" --pid-file=/work/my.pid \
    --skip-networking --secure-file-priv=NULL --local-infile=0 \
    --max-execution-time="${CODEKR_SQL_TIMEOUT_MS:-5000}" >>/work/.my.log 2>&1 &

ready=false
for _ in $(seq 1 150); do
    if mysqladmin --socket="$SOCK" -u root ping >/dev/null 2>&1; then
        ready=true
        break
    fi
    sleep 0.2
done
[ "$ready" = true ] || die "기동을 기다리다 지쳤습니다"

root_sql() { mysql --socket="$SOCK" -u root "$@"; }

# 결과 출력. **PostgreSQL 판과 같은 형식이어야 한다** — 채점기는 DB 를 모른다.
# psql 의 `-tA -F'|'` 자리에 mysql 은 `-N -B`(탭 구분)뿐이라 구분자를 바꿔 준다.
#
# **파이프로 잇지 않는다.** `mysql | sed` 로 두면 스크립트의 종료 코드가 sed 의 것이 되어,
# 제출 쿼리가 권한에 막혀도 0 으로 끝난다 — 채점기는 그것을 "돌았는데 답이 다르다" 로
# 읽어, 사용자는 무엇이 틀렸는지 모른 채 오답을 받는다.
rows() {
    mysql --socket="$SOCK" -N -B "$@" >/work/.rows || return $?
    sed 's/\t/|/g' /work/.rows
}

# 상태를 묻는 문제면 데이터베이스가 둘이다 (#453). 아니면 하나로 돈다.
if [ -f /work/verify.sql ]; then
    root_sql -e 'CREATE DATABASE expected; CREATE DATABASE actual;'
    TARGETS="expected actual"
else
    root_sql -e 'CREATE DATABASE codekr;'
    TARGETS="codekr"
fi

# 제출을 돌릴 계정. **소켓으로 붙으므로 호스트는 localhost 다.**
root_sql -e "CREATE USER 'solver'@'localhost';"

for db in $TARGETS; do
    if [ -f /work/schema.sql ]; then
        root_sql "$db" </work/schema.sql
    fi

    # **주지 않는 것이 막는 것이다.** `FILE`·`SUPER`·`PROCESS` 를 주지 않으므로
    # `LOAD_FILE()`, UDF 설치, `SET GLOBAL`, 남의 세션 보기가 전부 막힌다.
    # `mysql.*` 시스템 테이블도 권한이 없어 읽히지 않는다.
    root_sql -e "GRANT SELECT ON \`$db\`.* TO 'solver'@'localhost';"

    if [ -f /work/allow-write ]; then
        # 여는 것도 권한으로 한다 (#453). **DROP 은 주지 않는다** — 제출이 문제의
        # 시드 테이블을 지울 수 있으면 채점이 무엇을 견주는지 알 수 없게 된다.
        root_sql -e "GRANT INSERT, UPDATE, DELETE, CREATE ON \`$db\`.* TO 'solver'@'localhost';"
    fi
done
root_sql -e "FLUSH PRIVILEGES;"

if [ -f /work/verify.sql ]; then
    # 상태를 견준다 (#453). 정답 스크립트는 **root 로** 돈다 — 출제자의 코드다.
    if [ -f /work/answer.sql ]; then
        root_sql expected </work/answer.sql >>/work/.my.log 2>&1
    fi
    echo "--- codekr:expected"
    rows -u root expected </work/verify.sql
    echo "--- codekr:actual"
    # 제출은 solver 로, actual 에만 붙는다. 기대 상태를 건드릴 수 없다.
    #
    # **stderr 는 로그로 보내지 않는다.** 제출이 권한이나 문법에 막혔을 때 사용자에게
    # 보일 것이 그것뿐이다 — 로그로 넘기면 컨테이너와 함께 사라진다.
    mysql --socket="$SOCK" -u solver actual </work/query.sql >>/work/.my.log
    rows -u root actual </work/verify.sql
    exit 0
fi

if [ -f /work/answer.sql ]; then
    echo "--- codekr:expected"
    rows -u root codekr </work/answer.sql
    echo "--- codekr:actual"
fi
rows -u solver codekr </work/query.sql
