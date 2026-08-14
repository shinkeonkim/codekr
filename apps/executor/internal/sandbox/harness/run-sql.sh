# SQL 문제 실행 하네스 (#60, #453).
#
# 문제마다 **새 PostgreSQL 을 이 컨테이너 안에서** 띄운다. 제출끼리 인스턴스를 나눠 쓰면
# 한 사람의 쿼리가 다른 사람의 결과에 영향을 준다. 컨테이너와 함께 사라지는 편이 안전하다.
#
# 작업 디렉터리에 있을 수 있는 파일:
#   schema.sql  (선택) 스키마와 시드. 슈퍼유저로 넣는다. 문제가 소유한다
#   answer.sql  (선택) 정답. 있으면 먼저 돌려 기대 결과를 만든다
#   verify.sql  (선택) **끝난 뒤의 상태를 읽는 쿼리** (#453)
#   allow-write (선택) 있으면 제출이 쓸 수 있다 (#453). **권한으로 연다**
#   query.sql   제출. 기본은 **읽기 전용 롤**로 돈다
#
# 정답을 결과 집합이 아니라 쿼리로 두는 이유: 시드 데이터가 바뀌면 기대 결과도 따라간다.
#
# ## 상태를 묻는 문제 (#453)
#
# `INSERT`·`UPDATE`·`CREATE TABLE` 은 **결과 집합이 없다.** 바뀌는 것은 DB 의 상태다.
# 그래서 `verify.sql` 이 있으면 **데이터베이스를 둘** 만든다.
#
#   expected ← schema.sql + answer.sql  → verify.sql 을 돌려 기대 상태를 읽는다
#   actual   ← schema.sql + query.sql   → verify.sql 을 돌려 실제 상태를 읽는다
#
# **`initdb` 는 한 번이다.** 같은 클러스터의 두 데이터베이스라 기동 비용이 늘지 않는다.
# 그리고 제출은 `actual` 에만 붙으므로 기대 상태를 건드릴 수 없다.
set -eu

export PGDATA=/work/pgdata
export PGHOST=/work/sock
export LC_ALL=C
mkdir -p "$PGDATA" "$PGHOST"

initdb -U postgres --auth=trust -E UTF8 --no-sync -D "$PGDATA" >/work/.pg.log 2>&1
# listen_addresses 를 비워 유닉스 소켓만 연다. 네트워크는 이미 꺼져 있지만,
# 방어는 한 겹만 믿지 않는다.
pg_ctl -D "$PGDATA" -w -l /work/.pg.log \
    -o "-c listen_addresses='' -k $PGHOST -c fsync=off -c full_page_writes=off" \
    start >>/work/.pg.log 2>&1

# 상태를 묻는 문제면 데이터베이스가 둘이다. 아니면 지금까지처럼 postgres 하나로 돈다.
if [ -f /work/verify.sql ]; then
    psql -q -U postgres -d postgres -c 'CREATE DATABASE expected' >>/work/.pg.log 2>&1
    psql -q -U postgres -d postgres -c 'CREATE DATABASE actual' >>/work/.pg.log 2>&1
    TARGETS="expected actual"
else
    TARGETS="postgres"
fi

# **롤은 클러스터 하나에 하나다.** 데이터베이스마다 만들면 두 번째에서 실패한다.
# 그래서 롤은 여기서 한 번 만들고, 권한만 데이터베이스마다 준다.
psql -q -v ON_ERROR_STOP=1 -U postgres -d postgres >>/work/.pg.log 2>&1 <<'SQL'
CREATE ROLE solver LOGIN;
-- 기본은 읽기 전용이다. **문자열 필터가 아니라 권한으로 막는다** — 필터는 우회된다.
ALTER ROLE solver SET default_transaction_read_only = on;
SQL

# **여는 것도 권한으로 한다** (#453). 슈퍼유저는 주지 않으므로
# `COPY … FROM PROGRAM`·`pg_read_file`·`pg_authid` 는 그대로 막힌다.
# 쓰기를 여는 신호는 **파일**이다 — 스키마·정답·검사 쿼리가 오는 길과 같다.
# 환경 변수를 새로 뚫으면 그 값이 어디서 오는지 아는 곳이 하나 늘어난다.
if [ -f /work/allow-write ]; then
    psql -q -U postgres -d postgres \
        -c "ALTER ROLE solver SET default_transaction_read_only = off" >>/work/.pg.log 2>&1
fi

# 쿼리 하나가 서버를 붙잡고 있지 못하게 한다. 컨테이너의 시간 제한과 별개로,
# 서버 쪽에서도 끊어야 pg_ctl 이 정리에 실패하지 않는다.
psql -q -U postgres -d postgres \
    -c "ALTER ROLE solver SET statement_timeout = '${CODEKR_SQL_TIMEOUT_MS:-5000}ms'" >>/work/.pg.log 2>&1

for db in $TARGETS; do
    if [ -f /work/schema.sql ]; then
        psql -q -v ON_ERROR_STOP=1 -U postgres -d "$db" -f /work/schema.sql
    fi

    psql -q -v ON_ERROR_STOP=1 -U postgres -d "$db" >>/work/.pg.log 2>&1 <<'SQL'
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO solver;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO solver;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO solver;
SQL

    if [ -f /work/allow-write ]; then
        psql -q -v ON_ERROR_STOP=1 -U postgres -d "$db" >>/work/.pg.log 2>&1 <<'SQL'
GRANT INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO solver;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO solver;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO solver;
-- 스키마 설계 문제(DDL)는 자기 테이블을 만들 수 있어야 한다. 남의 테이블은 여전히
-- postgres 소유라 지우지 못한다 — DROP 은 소유자만 한다.
GRANT CREATE ON SCHEMA public TO solver;
SQL
    fi
done

if [ -f /work/verify.sql ]; then
    # 상태를 견준다 (#453). 정답 스크립트도 **슈퍼유저로** 돈다 — 출제자의 코드다.
    if [ -f /work/answer.sql ]; then
        psql -q -v ON_ERROR_STOP=1 -U postgres -d expected -f /work/answer.sql >>/work/.pg.log 2>&1
    fi
    echo "--- codekr:expected"
    psql -tA -F'|' -v ON_ERROR_STOP=1 -U postgres -d expected -f /work/verify.sql
    echo "--- codekr:actual"
    # **제출은 solver 로 돈다.** 그리고 actual 에만 붙으므로 기대 상태를 건드릴 수 없다.
    #
    # **stderr 는 로그로 보내지 않는다** — 제출이 막혔을 때 사용자에게 보일 것이 그것뿐이다.
    psql -q -v ON_ERROR_STOP=1 -U solver -d actual -f /work/query.sql >>/work/.pg.log
    psql -tA -F'|' -v ON_ERROR_STOP=1 -U postgres -d actual -f /work/verify.sql
    exit 0
fi

if [ -f /work/answer.sql ]; then
    echo "--- codekr:expected"
    psql -tA -F'|' -v ON_ERROR_STOP=1 -U postgres -d postgres -f /work/answer.sql
    echo "--- codekr:actual"
fi
psql -tA -F'|' -v ON_ERROR_STOP=1 -U solver -d postgres -f /work/query.sql
