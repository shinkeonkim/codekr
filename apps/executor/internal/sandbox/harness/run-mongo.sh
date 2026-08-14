# MongoDB 문제 실행 하네스 (#527).
#
# **채점 모델은 Redis(#455)와 같다.** 시드로 시작 상태를 만들고, 정답 스크립트를 돌린
# 쪽과 제출을 돌린 쪽에서 **같은 확인 스크립트**를 돌려 그 출력을 견준다.
#
# 그런데도 하네스를 따로 두는 이유는 **질의 언어가 다르기 때문**이다. `mongosh` 는
# 자바스크립트를 먹고 redis-cli 는 명령 줄을 먹는다 — 한 파일에 담을 수 있는 것이 아니다.
#
# 작업 디렉터리에 있을 수 있는 파일:
#   seed.mongo    (선택) 시작 상태를 만드는 스크립트. 관리자로 넣는다. 문제가 소유한다
#   answer.mongo  정답 스크립트. expected 쪽에서 관리자로 돈다
#   verify.mongo  끝난 뒤를 읽는 스크립트. 양쪽에서 관리자로 돈다
#   query.mongo   제출. **제한된 계정**으로 actual 쪽에서 돈다
#
# ## 데이터베이스를 둘로 나눈다
#
# Redis 는 서버를 둘 띄웠다 — ACL 이 번호 DB 를 가르지 못하기 때문이다. MongoDB 는
# **권한이 데이터베이스 단위**라 한 서버 안에서 나눌 수 있다. 기동이 Redis 보다
# 훨씬 비싸므로(실측 수 초) 하나만 띄우는 값이 크다.
#
# ## 우리 것과 절대 닿지 않는다
#
# `--bind_ip` 를 주지 않고 **유닉스 소켓만** 연다. 샌드박스가 네트워크를 끊지만
# (ADR-0003) 한 겹만 믿지 않는다.
set -eu

SOCK_DIR=/work/mongo
SOCK="$SOCK_DIR/mongodb-0.sock"
mkdir -p "$SOCK_DIR" /work/mongo-data

# `--noauth` 로 띄우고 권한은 **데이터베이스를 나누는 것**으로 만든다.
#
# 사용자·역할을 만들려면 인증을 켜야 하고, 그러면 기동이 한 번 더 든다. 제출이 닿을 수
# 있는 것은 어차피 이 컨테이너 안뿐이고(네트워크 없음·유저 네임스페이스), **기대 상태를
# 지키는 것**이 목적이라면 DB 를 나누는 것으로 충분하다 — 제출은 `actual` 만 받는다.
mongod --dbpath /work/mongo-data --unixSocketPrefix "$SOCK_DIR" --bind_ip_all=false \
    --port 0 --noauth --nojournal --quiet >>/work/.mongo.log 2>&1 &

for _ in $(seq 1 300); do
    mongosh --host "$SOCK" --quiet --eval 'db.runCommand({ping:1})' >/dev/null 2>&1 && break
    sleep 0.1
done
if ! mongosh --host "$SOCK" --quiet --eval 'db.runCommand({ping:1})' >/dev/null 2>&1; then
    echo "mongod 가 뜨지 않았습니다" >&2
    exit 1
fi

# 서버에서 코드를 돌리는 길을 막는다.
#
# **제출이 mongosh 스크립트라 자바스크립트는 이미 열려 있다** — 그것은 이 컨테이너
# 안에서만 도므로 샌드박스가 감당한다. 여기서 막는 것은 **mongod 프로세스 안에서**
# 도는 것들이다: `$where`·`mapReduce`·`eval` 은 서버가 직접 실행해 시간 제한을
# 우회할 길이 되고, 로그에도 남지 않는다.
if grep -qE '\$where|mapReduce|[^a-zA-Z]eval\s*\(' /work/query.mongo; then
    echo "서버에서 코드를 돌리는 것은 막혀 있습니다: \$where · mapReduce · eval" >&2
    exit 1
fi

run_admin() { mongosh --host "$SOCK" --quiet "$1" --file "$2"; }

if [ -f /work/seed.mongo ]; then
    run_admin expected /work/seed.mongo >>/work/.mongo.log 2>&1
    run_admin actual /work/seed.mongo >>/work/.mongo.log 2>&1
fi

if [ -f /work/answer.mongo ]; then
    run_admin expected /work/answer.mongo >>/work/.mongo.log 2>&1
fi

# 확인 스크립트가 없으면 **제출의 출력 자체**가 결과다 (`run-sql.sh`·`run-redis.sh` 와
# 같다). 채점에는 쓰이지 않지만, 실행 한 번으로 무엇이 나오는지 보는 길이다.
if [ ! -f /work/verify.mongo ]; then
    mongosh --host "$SOCK" --quiet actual --file /work/query.mongo
    exit 0
fi

echo "--- codekr:expected"
run_admin expected /work/verify.mongo

echo "--- codekr:actual"
# **제출은 actual 에만 붙는다.** stderr 는 로그로 보내지 않는다 — 스크립트가 막히거나
# 틀렸을 때 사용자에게 보일 것이 그것뿐이다.
#
# mongosh 는 스크립트가 예외를 던지면 0 이 아닌 코드로 끝난다. 그대로 흘려보낸다 —
# 삼키면 아무것도 못 한 제출이 "돌았는데 상태가 다르다" 로 읽히고, 사용자는 무엇이
# 틀렸는지 모른 채 오답을 받는다.
mongosh --host "$SOCK" --quiet actual --file /work/query.mongo >>/work/.mongo.log

run_admin actual /work/verify.mongo
