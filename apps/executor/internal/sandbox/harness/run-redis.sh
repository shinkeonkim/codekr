# Redis 문제 실행 하네스 (#455).
#
# **채점 모델이 SQL 과 다르다.** SQL 은 쿼리 하나를 던져 결과 집합을 받지만, Redis 는
# 제출이 **명령의 연속**이고 남는 것은 **상태**다. 그래서 견주는 것도 결과가 아니라
# 끝난 뒤의 상태다 — #453 이 SQL 에서 낸 길과 같은 모양이고, 여기서는 그것이 **기본**이다.
#
# 작업 디렉터리에 있을 수 있는 파일:
#   seed.redis    (선택) 시작 상태를 만드는 명령. 관리자로 넣는다. 문제가 소유한다
#   answer.redis  정답 명령. expected 쪽에서 관리자로 돈다
#   verify.redis  끝난 뒤의 상태를 읽는 명령. 양쪽에서 관리자로 돈다
#   commands.redis  제출. **제한된 계정**으로 actual 쪽에서 돈다
#
# ## 서버를 둘 띄운다
#
# Redis 의 번호 DB(`-n 1`)로 나누지 않는다. **ACL 은 키 패턴으로만 걸리고 번호 DB 를
# 가르지 못하므로**, 쓰기가 열린 제출이 기대 상태를 건드릴 수 있다. 프로세스를 둘 띄우는
# 값은 밀리초 단위라 나눌 이유가 더 크다.
#
# ## 우리가 큐로 쓰는 Redis 와 절대 닿지 않는다
#
# `--port 0` 으로 **TCP 를 아예 열지 않고** 유닉스 소켓만 쓴다. 샌드박스가 네트워크를
# 끊지만(ADR-0003) 한 겹만 믿지 않는다 — 같은 제품을 큐로도 쓰기 때문이다(ADR-0002).
set -eu

start() {
    redis-server --port 0 --unixsocket "/work/$1.sock" --save '' --appendonly no \
        --dir /work --daemonize yes >>/work/.redis.log 2>&1
    for _ in $(seq 1 100); do
        redis-cli -s "/work/$1.sock" ping >/dev/null 2>&1 && return 0
        sleep 0.1
    done
    echo "redis 가 뜨지 않았습니다: $1" >&2
    return 1
}

admin() { redis-cli -s "/work/$1.sock" "${@:2}"; }

# 파일 한 줄이 명령 하나다. redis-cli 는 파일을 그대로 먹지 못하므로 줄을 흘려 넣는다.
feed_admin() { redis-cli -s "/work/$1.sock" <"$2"; }

# **Redis 에는 주석이 없다.** redis-cli 는 `#` 으로 시작하는 줄도 명령으로 읽어
# `ERR unknown command '#'` 를 낸다 — 시작 코드에 설명 한 줄을 적어 두는 것만으로
# 모든 제출이 실패한다는 뜻이다. 그래서 **줄 전체가 주석인 것과 빈 줄만** 걷어낸다.
# 값 안의 `#`(예: `SET tag "#codekr"`)은 건드리지 않는다.
for name in seed answer verify commands; do
    [ -f "/work/$name.redis" ] || continue
    sed -i -e '/^[[:space:]]*#/d' -e '/^[[:space:]]*$/d' "/work/$name.redis"
done

start expected
start actual

# 제출이 쓰는 계정. **읽기 전용이 아니다** — Redis 문제는 대개 상태를 바꾸는 것이
# 문제 자체다. 대신 위험한 것을 통째로 뺀다:
#   -@admin      CONFIG·MODULE·SHUTDOWN·CLIENT·INFO
#   -@dangerous  KEYS·FLUSHALL·FLUSHDB·SWAPDB·ACL
#   -@scripting  EVAL·FUNCTION (Lua 실행)
#   -@pubsub     다른 연결과 신호를 주고받는 길
redis-cli -s /work/actual.sock ACL SETUSER solver on '>codekr' '~*' '&*' \
    +@all -@admin -@dangerous -@scripting -@pubsub >>/work/.redis.log 2>&1

if [ -f /work/seed.redis ]; then
    feed_admin expected /work/seed.redis >>/work/.redis.log 2>&1
    feed_admin actual /work/seed.redis >>/work/.redis.log 2>&1
fi

if [ -f /work/answer.redis ]; then
    feed_admin expected /work/answer.redis >>/work/.redis.log 2>&1
fi

# 확인 명령이 없으면 **제출의 출력 자체**가 결과다 (`run-sql.sh` 가 정답 쿼리 없이
# 도는 것과 같다). 채점에는 쓰이지 않지만, 실행 한 번으로 무엇이 나오는지 보는 길이다.
if [ ! -f /work/verify.redis ]; then
    redis-cli -s /work/actual.sock --user solver --pass codekr --no-auth-warning \
        <"/work/commands.redis"
    exit 0
fi

echo "--- codekr:expected"
feed_admin expected /work/verify.redis

echo "--- codekr:actual"
# **제출은 solver 로, actual 에만 붙는다.** stderr 는 로그로 보내지 않는다 — 명령이
# 권한이나 문법에 막혔을 때 사용자에게 보일 것이 그것뿐이다.
redis-cli -s /work/actual.sock --user solver --pass codekr --no-auth-warning \
    <"/work/commands.redis" >>/work/.redis.log

# **막힌 명령을 실패로 만든다.** redis-cli 는 NOPERM 을 받아도 0 으로 끝나므로, 그대로
# 두면 아무것도 못 한 제출이 "돌았는데 상태가 다르다" 로 읽힌다 — 사용자는 무엇이
# 틀렸는지 모른 채 오답을 받는다.
if grep -qE '^(NOPERM|ERR|WRONGTYPE|EXECABORT)' /work/.redis.log; then
    grep -E '^(NOPERM|ERR|WRONGTYPE|EXECABORT)' /work/.redis.log >&2
    exit 1
fi

feed_admin actual /work/verify.redis
