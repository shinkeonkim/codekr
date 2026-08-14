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

# **소켓과 데이터는 컨테이너 안에 둔다.** `/work` 는 밖에서 붙인 디렉터리라
# mongod 가 소켓에 chmod 를 걸지 못하고("Failed to chmod socket file") 그 자리에서
# 죽는다. 작업 디렉터리에 서버 파일이 남지 않는 편이 낫기도 하다.
SOCK_DIR=/tmp/mongo
# 소켓 이름에 포트가 들어간다. 포트를 정해 두지 않으면 이름을 맞출 수 없다.
# **mongosh 는 소켓 경로를 URL 로 받는다.** `--host` 에 넣으면 `/` 가 못 쓰는 글자라며
# 거부하고, 경로를 그대로 주면 URI 가 아니라고 거부한다 — 슬래시를 %2F 로 적어야 한다.
SOCK="mongodb://%2Ftmp%2Fmongo%2Fmongodb-27017.sock"
mkdir -p "$SOCK_DIR" /tmp/mongo-data

# `--noauth` 로 띄우고 권한은 **데이터베이스를 나누는 것**으로 만든다.
#
# 사용자·역할을 만들려면 인증을 켜야 하고, 그러면 기동이 한 번 더 든다. 제출이 닿을 수
# 있는 것은 어차피 이 컨테이너 안뿐이고(네트워크 없음·유저 네임스페이스), **기대 상태를
# 지키는 것**이 목적이라면 DB 를 나누는 것으로 충분하다 — 제출은 `actual` 만 받는다.
# `--bind_ip ""` 로 **TCP 를 아예 열지 않는다.** `--bind_ip_all` 은 인자를 받지 않는
# 스위치라 `=false` 를 붙이면 mongod 가 뜨지도 못한다. `--nojournal` 은 7 에서 빠졌다.
mongod --dbpath /tmp/mongo-data --unixSocketPrefix "$SOCK_DIR" --bind_ip "" --port 27017 \
    --noauth --quiet >>/work/.mongo.log 2>&1 &

for _ in $(seq 1 300); do
    mongosh "$SOCK" --quiet --eval 'db.runCommand({ping:1})' >/dev/null 2>&1 && break
    sleep 0.1
done
if ! mongosh "$SOCK" --quiet --eval 'db.runCommand({ping:1})' >/dev/null 2>&1; then
    echo "mongod 가 뜨지 않았습니다" >&2
    exit 1
fi

# 서버에서 코드를 돌리는 길을 막는다.
#
# **제출이 mongosh 스크립트라 자바스크립트는 이미 열려 있다** — 그것은 이 컨테이너
# 안에서만 도므로 샌드박스가 감당한다. 여기서 막는 것은 **mongod 프로세스 안에서**
# 도는 것들이다: `$where`·`mapReduce`·`eval` 은 서버가 직접 실행해 시간 제한을
# 우회할 길이 되고, 로그에도 남지 않는다.
#
# **표식을 낸 뒤에 부른다.** 먼저 끊으면 채점기가 `--- codekr:expected` 를 못 받고,
# 그것은 "출제자의 시드가 깨졌다"(SYSTEM_ERROR)는 뜻이 된다 — 사용자가 쓴 것이
# 막힌 것인데 사용자 잘못이 아닌 판정이 나간다.
reject_server_side_js() {
    if grep -qE '\$where|mapReduce|[^a-zA-Z]eval\s*\(' /work/query.mongo; then
        echo "서버에서 코드를 돌리는 것은 막혀 있습니다: \$where · mapReduce · eval" >&2
        exit 1
    fi
}

run_admin() { mongosh "$SOCK/$1" --quiet --file "$2"; }

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
    # 견줄 상대가 없는 실행이다. 표식도 없으므로 여기서는 바로 끊어도 된다.
    reject_server_side_js
    mongosh "$SOCK/actual" --quiet --file /work/query.mongo
    exit 0
fi

echo "--- codekr:expected"
run_admin expected /work/verify.mongo

echo "--- codekr:actual"
# 표식 둘이 나간 뒤에 막는다 — 그래야 채점기가 "사용자가 쓴 것이 막혔다" 로 읽는다.
reject_server_side_js
# **제출은 actual 에만 붙는다.** stderr 는 로그로 보내지 않는다 — 스크립트가 막히거나
# 틀렸을 때 사용자에게 보일 것이 그것뿐이다.
#
# mongosh 는 스크립트가 예외를 던지면 0 이 아닌 코드로 끝난다. 그대로 흘려보낸다 —
# 삼키면 아무것도 못 한 제출이 "돌았는데 상태가 다르다" 로 읽히고, 사용자는 무엇이
# 틀렸는지 모른 채 오답을 받는다.
mongosh "$SOCK/actual" --quiet --file /work/query.mongo >>/work/.mongo.log

run_admin actual /work/verify.mongo
