# SQL 문제 실행 하네스 (#60).
#
# 문제마다 **새 PostgreSQL 을 이 컨테이너 안에서** 띄운다. 제출끼리 인스턴스를 나눠 쓰면
# 한 사람의 쿼리가 다른 사람의 결과에 영향을 준다. 컨테이너와 함께 사라지는 편이 안전하다.
#
# 작업 디렉터리에 있을 수 있는 파일:
#   schema.sql  (선택) 스키마와 시드. 슈퍼유저로 넣는다. 문제가 소유한다
#   answer.sql  (선택) 정답 쿼리. 있으면 먼저 돌려 기대 결과를 만든다
#   query.sql   제출 쿼리. **읽기 전용 롤**로 돈다
#
# 정답을 결과 집합이 아니라 쿼리로 두는 이유: 시드 데이터가 바뀌면 기대 결과도 따라간다.
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

if [ -f /work/schema.sql ]; then
    psql -q -v ON_ERROR_STOP=1 -U postgres -d postgres -f /work/schema.sql
fi

# 제출 쿼리를 도는 롤. **쓰기 차단은 문자열 필터가 아니라 권한으로 한다** —
# 필터는 우회되지만 권한은 우회되지 않는다.
psql -q -v ON_ERROR_STOP=1 -U postgres -d postgres >>/work/.pg.log 2>&1 <<'SQL'
CREATE ROLE solver LOGIN;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO solver;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO solver;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO solver;
ALTER ROLE solver SET default_transaction_read_only = on;
SQL

# 쿼리 하나가 서버를 붙잡고 있지 못하게 한다. 컨테이너의 시간 제한과 별개로,
# 서버 쪽에서도 끊어야 pg_ctl 이 정리에 실패하지 않는다.
psql -q -U postgres -d postgres \
    -c "ALTER ROLE solver SET statement_timeout = '${CODEKR_SQL_TIMEOUT_MS:-5000}ms'" >>/work/.pg.log 2>&1

if [ -f /work/answer.sql ]; then
    echo "--- codekr:expected"
    psql -tA -F'|' -v ON_ERROR_STOP=1 -U postgres -d postgres -f /work/answer.sql
    echo "--- codekr:actual"
fi
psql -tA -F'|' -v ON_ERROR_STOP=1 -U solver -d postgres -f /work/query.sql
